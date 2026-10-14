package gt.usac.redxela.planificacion;

import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.estadisticas.Estadisticas;
import gt.usac.redxela.modelo.EstadoPedido;
import gt.usac.redxela.modelo.Pedido;
import gt.usac.redxela.proceso.TrabajadorPedido;
import gt.usac.redxela.recursos.Concesion;
import gt.usac.redxela.recursos.GestorRecursos;
import org.springframework.beans.factory.annotation.Autowired;
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
 * eventos (llegada de nuevo pedido, liberación de recursos, cambios de política
 * o ticks temporales para envejecimiento).
 * 
 * Es un planificador CONSERVADOR DEL TRABAJO (Work-Conserving): si el proceso de
 * mayor prioridad no puede iniciar por falta de algún recurso específico, evalúa
 * a los siguientes candidatos factibles para maximizar el aprovechamiento de los
 * recursos del centro de distribución sin desperdiciar capacidad ociosa.
 */
@Component
public class Despachador implements Runnable {

    private final ListaEspera listaEspera;
    private final GestorRecursos gestorRecursos;
    private final SimClock simClock;
    private final Estadisticas estadisticas;

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
        }
    }

    /**
     * Notifica al monitor que ha ocurrido un evento relevante (nuevo pedido,
     * recursos liberados, o cambio de política) para re-evaluar la cola.
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
     * Evalúa a los candidatos en espera según la política de planificación activa.
     */
    public void evaluarCola() {
        long ahoraSim = simClock.ahoraSimuladoSegundos();
        List<Pedido> candidatos = listaEspera.obtenerCandidatosOrdenados(politicaActiva, ahoraSim);

        for (Pedido candidato : candidatos) {
            if (!activo) break;

            if (gestorRecursos.isPrevencionDeadlock()) {
                // MODO CON PREVENCIÓN: Intento todo-o-nada atómico
                Optional<Concesion> concesionOpt = gestorRecursos.intentarAdquirirTodoONada(candidato, ahoraSim);
                if (concesionOpt.isPresent()) {
                    listaEspera.remover(candidato);
                    lanzarTrabajador(candidato, concesionOpt.get());
                } else {
                    // No hay recursos suficientes en este momento
                    candidato.marcarEnEsperaRecursos();
                }
            } else {
                // MODO SIN PREVENCIÓN: El trabajador se lanza y adquiere uno a uno
                listaEspera.remover(candidato);
                lanzarTrabajador(candidato, null);
            }
        }
    }

    private void lanzarTrabajador(Pedido pedido, Concesion concesionPrevia) {
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
                if (estadisticas != null) {
                    estadisticas.registrarPedidoCompletado(pedido, finSimulado);
                }
            } else if (pedido.getEstado() == EstadoPedido.EN_ESPERA_RECURSOS) {
                // Si fue interrumpido o reencolado
                listaEspera.agregar(pedido);
                if (estadisticas != null) {
                    estadisticas.registrarReencolado();
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
