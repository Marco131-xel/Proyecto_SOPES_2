package gt.usac.redxela.planificacion;

import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.estadisticas.Estadisticas;
import gt.usac.redxela.inventario.Inventario;
import gt.usac.redxela.modelo.EstadoPedido;
import gt.usac.redxela.modelo.Pedido;
import gt.usac.redxela.proceso.TrabajadorPedido;
import gt.usac.redxela.recursos.Concesion;
import gt.usac.redxela.recursos.GestorRecursos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Despachador de procesos (Dispatcher / Short-Term Scheduler) de REDXela.
 * 
 * Opera como un monitor de sincronización utilizando ReentrantLock y Condition:
 * se duerme de forma eficiente cuando no hay trabajo factible y despierta ante
 * eventos (llegada de nuevo pedido, liberación de recursos, llegada de existencias a almacén,
 * cambios de política o ticks temporales para envejecimiento).
 * 
 * Es un planificador CONSERVADOR DEL TRABAJO (Work-Conserving):
 * 1. Si no hay stock para el pedido prioritario -> pasa a EN_ESPERA_STOCK y evalúa al siguiente.
 * 2. Si no hay recursos disponibles -> revierte reserva de stock, pasa a EN_ESPERA_RECURSOS y evalúa al siguiente.
 * 3. Si ambos están disponibles -> despacha el pedido.
 */
@Component
public class Despachador implements Runnable {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private gt.usac.redxela.eventos.RegistroEventos eventos;

    private void registrarEvento(String tipo, String mensaje) {
        if (eventos != null) eventos.registrar(tipo, mensaje);
    }


    private final ListaEspera listaEspera;
    private final GestorRecursos gestorRecursos;
    private final SimClock simClock;
    private final Estadisticas estadisticas;

    @Autowired(required = false)
    @Lazy
    private Inventario inventario;

    private final ReentrantLock monitorLock = new ReentrantLock();
    private final Condition eventoPendiente = monitorLock.newCondition();

    private volatile PoliticaAtencion politicaActiva;
    private volatile boolean activo = true;

    private final Map<String, Pedido> pedidosEnProceso = new ConcurrentHashMap<>();
    private final Map<String, Thread> hilosTrabajadores = new ConcurrentHashMap<>();
    private final Deque<Pedido> pedidosCompletados = new ConcurrentLinkedDeque<>();

    private Thread hiloDespachador;

    @Autowired
    public Despachador(ListaEspera listaEspera, GestorRecursos gestorRecursos,
                       SimClock simClock, PoliticaPrioridadEstricta politicaInicial,
                       Estadisticas estadisticas) {
        this.listaEspera = listaEspera;
        this.gestorRecursos = gestorRecursos;
        this.simClock = simClock;
        this.politicaActiva = politicaInicial;
        this.estadisticas = estadisticas;
    }

    /**
     * Constructor auxiliar para pruebas unitarias sin inyección completa.
     */
    public Despachador(ListaEspera listaEspera, GestorRecursos gestorRecursos,
                       SimClock simClock, PoliticaAtencion politicaInicial) {
        this(listaEspera, gestorRecursos, simClock,
                politicaInicial instanceof PoliticaPrioridadEstricta p ? p : new PoliticaPrioridadEstricta(),
                new Estadisticas());
        this.politicaActiva = politicaInicial;
    }

    public void setInventario(Inventario inventario) {
        this.inventario = inventario;
    }

    /**
     * Inicia el hilo despachador de plataforma.
     */
    public synchronized void iniciar() {
        if (hiloDespachador == null || !hiloDespachador.isAlive()) {
            this.activo = true;
            this.hiloDespachador = new Thread(this, "hilo-despachador");
            this.hiloDespachador.setDaemon(true);
            this.hiloDespachador.start();
        }
    }

    /**
     * Detiene la ejecución del despachador de forma controlada.
     */
    public synchronized void detener() {
        this.activo = false;
        despertar();
        if (hiloDespachador != null) {
            hiloDespachador.interrupt();
            try { hiloDespachador.join(2000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        // No lanzar más trabajadores durante el cierre; interrumpir libera sus concesiones.
        List<Thread> trabajadores = List.copyOf(hilosTrabajadores.values());
        trabajadores.forEach(Thread::interrupt);
        for (Thread trabajador : trabajadores) {
            try { trabajador.join(2000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    /**
     * Notifica al monitor que ha ocurrido un evento relevante para re-evaluar la cola.
     */
    public void despertar() {
        monitorLock.lock();
        try {
            eventoPendiente.signalAll();
        } finally {
            monitorLock.unlock();
        }
    }

    @Override
    public void run() {
        while (activo && !Thread.currentThread().isInterrupted()) {
            try {
                monitorLock.lock();
                try {
                    // Espera reactiva con timeout para evaluar aging y deadlines
                    eventoPendiente.await(250, TimeUnit.MILLISECONDS);
                } finally {
                    monitorLock.unlock();
                }

                if (!activo) break;

                // Registro periódico de telemetría de cola
                long ahoraSim = simClock.ahoraSimuladoSegundos();
                if (estadisticas != null) {
                    estadisticas.registrarMuestraCola(ahoraSim, listaEspera.size(), pedidosEnProceso.size());
                }

                evaluarCola();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.printf("[ERROR] Excepción en ciclo del despachador: %s%n", e.getMessage());
            }
        }
    }

    /**
     * Evalúa a los candidatos en espera según la política de planificación activa (R5, R12).
     */
    public void evaluarCola() {
        long ahoraSim = simClock.ahoraSimuladoSegundos();
        List<Pedido> candidatos = listaEspera.obtenerCandidatosOrdenados(politicaActiva, ahoraSim);

        for (Pedido candidato : candidatos) {
            if (!activo) break;

            // 1. Verificación y reserva atómica de stock en almacén (si inventario está presente)
            boolean stockReservado = false;
            if (inventario != null) {
                stockReservado = inventario.intentarReservarStock(candidato);
                if (!stockReservado) {
                    candidato.marcarEnEsperaStock();
                    continue; // Pasa al siguiente candidato factible (work-conserving)
                }
            }

            // 2. Verificación de recursos compartidos (empaque, montacargas, etc.)
            if (gestorRecursos.isPrevencionDeadlock()) {
                // MODO CON PREVENCIÓN: Intento todo-o-nada atómico
                Optional<Concesion> concesionOpt = gestorRecursos.intentarAdquirirTodoONada(candidato, ahoraSim);
                if (concesionOpt.isPresent()) {
                    listaEspera.remover(candidato);
                    lanzarTrabajador(candidato, concesionOpt.get());
                } else {
                    // Si fallaron los recursos, se deshace la reserva de stock para no acaparar existencias
                    if (stockReservado && inventario != null) {
                        inventario.cancelarReserva(candidato);
                    }
                    candidato.marcarEnEsperaRecursos();
                }
            } else {
                // MODO SIN PREVENCIÓN: El trabajador se lanza y adquiere incrementalmente
                listaEspera.remover(candidato);
                lanzarTrabajador(candidato, null);
            }
        }
    }

    /** Entrada común para que pedidos manuales, automáticos y del escenario cuenten igual. */
    public void registrarPedido(Pedido pedido) {
        estadisticas.registrarPedidoCreado(pedido);
        registrarEvento("PEDIDO", pedido.getId() + " creado: " + pedido.getTotalUnidades() + " unidades");
        listaEspera.agregar(pedido);
        despertar();
    }

    private void lanzarTrabajador(Pedido pedido, Concesion concesionPrevia) {
        registrarEvento("DESPACHO", pedido.getId() + " despachado");
        pedidosEnProceso.put(pedido.getId(), pedido);

        Runnable callbackFin = () -> {
            pedidosEnProceso.remove(pedido.getId());
            hilosTrabajadores.remove(pedido.getId());

            long finSimulado = simClock.ahoraSimuladoSegundos();

            if (pedido.getEstado() == EstadoPedido.COMPLETADO) {
                pedidosCompletados.addFirst(pedido);
                while (pedidosCompletados.size() > ConfiguracionSimulacion.MAX_PEDIDOS_COMPLETADOS_HISTORICO) {
                    pedidosCompletados.pollLast();
                }

                // Consumir definitivamente el stock y liberar celdas del almacén
                if (inventario != null) {
                    inventario.consumirReservaYStock(pedido);
                }

                if (estadisticas != null) {
                    estadisticas.registrarPedidoCompletado(pedido, finSimulado);
                    registrarEvento("COMPLETADO", pedido.getId() + " completado");
                }
            } else if (pedido.getEstado() == EstadoPedido.EN_ESPERA_RECURSOS) {
                // Si fue reencolado o interrumpido, liberamos la reserva de stock
                if (inventario != null) {
                    inventario.cancelarReserva(pedido);
                }
                if (activo) listaEspera.agregar(pedido);
                if (estadisticas != null) {
                    estadisticas.registrarReencolado();
                    registrarEvento("REENCOLADO", pedido.getId() + " vuelve a espera tras liberar recursos");
                }
            } else if (pedido.getEstado() == EstadoPedido.CANCELADO) {
                if (inventario != null) {
                    inventario.cancelarReserva(pedido);
                }
            }

            // Despierta al monitor para evaluar a los siguientes pedidos en espera
            despertar();
        };

        TrabajadorPedido trabajador = new TrabajadorPedido(
                pedido, gestorRecursos, simClock, concesionPrevia, callbackFin);

        Thread hilo = new Thread(trabajador, "pedido-" + pedido.getId());
        hilosTrabajadores.put(pedido.getId(), hilo);
        hilo.start();
    }

    /**
     * Cambia la política de planificación en caliente (R5).
     */
    public void setPoliticaActiva(PoliticaAtencion nuevaPolitica) {
        this.politicaActiva = Objects.requireNonNull(nuevaPolitica, "La política no puede ser nula");
        despertar();
    }

    public PoliticaAtencion getPoliticaActiva() {
        return politicaActiva;
    }

    public Map<String, Pedido> getPedidosEnProceso() {
        return Collections.unmodifiableMap(pedidosEnProceso);
    }

    public List<Pedido> getPedidosCompletados() {
        return new ArrayList<>(pedidosCompletados);
    }

    public Thread getHiloTrabajador(String pedidoId) {
        return hilosTrabajadores.get(pedidoId);
    }

    public Estadisticas getEstadisticas() {
        return estadisticas;
    }
}
