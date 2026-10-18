package gt.usac.redxela.inventario;

import gt.usac.redxela.almacen.Almacen;
import gt.usac.redxela.almacen.EntradaAsignacion;
import gt.usac.redxela.almacen.Lote;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.modelo.CatalogoProductos;
import gt.usac.redxela.modelo.LineaPedido;
import gt.usac.redxela.modelo.Pedido;
import gt.usac.redxela.modelo.Producto;
import gt.usac.redxela.planificacion.Despachador;
import gt.usac.redxela.recepcion.MensajeRecepcion;
import gt.usac.redxela.recepcion.Recepcion;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Consumidor del patrón Productor-Consumidor y Gestor de Existencias (R10, R12, R13).
 * 
 * Funciones clave:
 * 1. CONSUMO ASÍNCRONO POR COLA:
 *    Consume MensajeRecepcion mediante take() desde la cola acotada a su propio ritmo.
 *    No comparte estructuras internas con Recepción.
 * 
 * 2. ASIGNACIÓN EN ALMACÉN Y ALMACÉN LLENO:
 *    Asigna celdas en el Almacén con asignación indexada no contigua.
 *    Si el almacén está lleno, encola en lotesPendientesDeEspacio y reintenta
 *    cuando se liberen celdas físicas por consumo de pedidos.
 * 
 * 3. PREVENCIÓN DE SOBREVENTA (Race Condition Prevention):
 *    Mantiene contadores atómicos de existencias físicas y reservas por producto
 *    bajo un lock de exclusión mutua:
 *    Disponible = ExistenciasFisicas - ExistenciasReservadas.
 *    Un pedido reserva stock antes de iniciar su trabajo y lo consume al finalizar.
 * 
 * 4. DESPERTAR REACTIVO DE PEDIDOS (R12):
 *    Al procesar un lote de stock, emite una señal al Despachador para re-evaluar
 *    inmediatamente los pedidos en estado EN_ESPERA_STOCK.
 */
@Component
public class Inventario implements Runnable {

    public record LotePendiente(String loteId, Producto producto, int cantidad, long instante) {}

    private final Almacen almacen;
    private final Recepcion recepcion;
    private final SimClock simClock;
    private final Despachador despachador;

    private final ReentrantLock lockStock = new ReentrantLock(true);
    private final Map<String, AtomicInteger> existenciasFisicas = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> existenciasReservadas = new ConcurrentHashMap<>();

    // Cola de lotes activos ordenados por llegada (FIFO) para consumo ordenado
    private final List<Lote> lotesEnAlmacen = new ArrayList<>();
    private final Deque<LotePendiente> lotesPendientesEspacio = new ConcurrentLinkedDeque<>();

    private final AtomicLong secuencialLotes = new AtomicLong(0);
    private final AtomicLong totalMensajesProcesados = new AtomicLong(0);
    private final AtomicBoolean activo = new AtomicBoolean(false);

    private Thread hiloInventario;

    public Inventario(Almacen almacen, Recepcion recepcion, SimClock simClock, @Lazy Despachador despachador) {
        this.almacen = almacen;
        this.recepcion = recepcion;
        this.simClock = simClock;
        this.despachador = despachador;

        for (Producto p : CatalogoProductos.obtenerTodos()) {
            existenciasFisicas.put(p.id(), new AtomicInteger(0));
            existenciasReservadas.put(p.id(), new AtomicInteger(0));
        }
    }

    public synchronized void iniciar() {
        if (!activo.get()) {
            activo.set(true);
            hiloInventario = new Thread(this, "hilo-inventario");
            hiloInventario.setDaemon(true);
            hiloInventario.start();
        }
    }

    public synchronized void detener() {
        activo.set(false);
        if (hiloInventario != null) {
            hiloInventario.interrupt();
        }
    }

    @Override
    public void run() {
        while (activo.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // take() bloquea al consumidor si la cola está vacía
                MensajeRecepcion mensaje = recepcion.getColaMensajes().take();

                // Procesamiento a ritmo propio (simula tiempo de descarga y ubicación en almacén)
                simClock.dormirSimuladoMs(150);

                procesarMensaje(mensaje);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.printf("[ERROR] Fallo en hilo de inventario: %s%n", e.getMessage());
            }
        }
    }

    private void procesarMensaje(MensajeRecepcion mensaje) {
        Producto producto = CatalogoProductos.buscarPorId(mensaje.productoId()).orElse(null);
        if (producto == null) return;

        String loteId = String.format("LOT-%05d", secuencialLotes.incrementAndGet());
        long ahoraSim = simClock.ahoraSimuladoSegundos();

        lockStock.lock();
        try {
            Optional<Lote> loteAsignado = almacen.intentarAsignarLote(loteId, producto, mensaje.cantidad(), ahoraSim);

            if (loteAsignado.isPresent()) {
                lotesEnAlmacen.add(loteAsignado.get());
                existenciasFisicas.get(producto.id()).addAndGet(mensaje.cantidad());
                totalMensajesProcesados.incrementAndGet();

                // R12: Notificación al despachador para re-evaluar pedidos en espera de este producto
                if (despachador != null) {
                    despachador.despertar();
                }
            } else {
                // Almacén lleno: queda registrado como pendiente de espacio (R10, R13)
                lotesPendientesEspacio.addLast(new LotePendiente(loteId, producto, mensaje.cantidad(), ahoraSim));
            }
        } finally {
            lockStock.unlock();
        }
    }

    /**
     * Intenta reservar de forma atómica el stock requerido para todas las líneas de un pedido.
     * Si alguna línea no dispone de existencias libres suficientes, revierte cualquier
     * reserva parcial para evitar bloqueos y condiciones de carrera (prevención de sobreventa).
     * 
     * @param pedido Pedido a evaluar.
     * @return true si se reservó con éxito el 100% del stock solicitado.
     */
    public boolean intentarReservarStock(Pedido pedido) {
        lockStock.lock();
        try {
            // Fase de verificación
            for (LineaPedido lp : pedido.getLineas()) {
                String pid = lp.producto().id();
                int disponible = getDisponible(pid);
                if (disponible < lp.cantidad()) {
                    return false; // Stock insuficiente para esta línea
                }
            }

            // Fase de reserva atómica
            for (LineaPedido lp : pedido.getLineas()) {
                String pid = lp.producto().id();
                existenciasReservadas.get(pid).addAndGet(lp.cantidad());
            }

            return true;
        } finally {
            lockStock.unlock();
        }
    }

    /**
     * Cancela la reserva de stock previamente asignada a un pedido (ej. por reencolado).
     */
    public void cancelarReserva(Pedido pedido) {
        lockStock.lock();
        try {
            for (LineaPedido lp : pedido.getLineas()) {
                String pid = lp.producto().id();
                existenciasReservadas.get(pid).addAndGet(-lp.cantidad());
            }
        } finally {
            lockStock.unlock();
        }
    }

    /**
     * Consume el stock físico reservado por un pedido completado y libera las celdas
     * del almacén que hayan quedado vacías. Reintenta ubicar lotes pendientes si se abrió espacio.
     */
    public void consumirReservaYStock(Pedido pedido) {
        lockStock.lock();
        try {
            for (LineaPedido lp : pedido.getLineas()) {
                String pid = lp.producto().id();
                int cantidadAConsumir = lp.cantidad();

                existenciasReservadas.get(pid).addAndGet(-cantidadAConsumir);
                existenciasFisicas.get(pid).addAndGet(-cantidadAConsumir);

                // Consumir de las celdas de lotes en orden FIFO
                Iterator<Lote> itLotes = lotesEnAlmacen.iterator();
                while (itLotes.hasNext() && cantidadAConsumir > 0) {
                    Lote lote = itLotes.next();
                    if (lote.getProducto().id().equals(pid)) {
                        for (EntradaAsignacion entrada : lote.getTablaAsignacion()) {
                            if (entrada.ubicacion().getCantidadOcupada() > 0) {
                                int enCelda = entrada.ubicacion().getCantidadOcupada();
                                if (cantidadAConsumir >= enCelda) {
                                    cantidadAConsumir -= enCelda;
                                    almacen.liberarUbicacion(entrada.ubicacion());
                                } else {
                                    // Parcial en la celda
                                    entrada.ubicacion().asignar(
                                            lp.producto(), enCelda - cantidadAConsumir, lote.getId());
                                    cantidadAConsumir = 0;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // Si se liberó espacio en el almacén, intentamos procesar lotes pendientes
            reintentarLotesPendientes();

        } finally {
            lockStock.unlock();
        }
    }

    private void reintentarLotesPendientes() {
        Iterator<LotePendiente> it = lotesPendientesEspacio.iterator();
        while (it.hasNext()) {
            LotePendiente pend = it.next();
            Optional<Lote> asignado = almacen.intentarAsignarLote(
                    pend.loteId(), pend.producto(), pend.cantidad(), simClock.ahoraSimuladoSegundos());

            if (asignado.isPresent()) {
                it.remove();
                lotesEnAlmacen.add(asignado.get());
                existenciasFisicas.get(pend.producto().id()).addAndGet(pend.cantidad());
                totalMensajesProcesados.incrementAndGet();

                if (despachador != null) {
                    despachador.despertar();
                }
            } else {
                break; // No hay espacio suficiente para el siguiente lote
            }
        }
    }

    public int getExistenciaFisica(String productoId) {
        AtomicInteger val = existenciasFisicas.get(productoId);
        return val != null ? val.get() : 0;
    }

    public int getReservado(String productoId) {
        AtomicInteger val = existenciasReservadas.get(productoId);
        return val != null ? val.get() : 0;
    }

    public int getDisponible(String productoId) {
        return Math.max(0, getExistenciaFisica(productoId) - getReservado(productoId));
    }

    public int getLotesPendientesEspacioCount() {
        return lotesPendientesEspacio.size();
    }

    public InventarioSnapshot snapshot() {
        lockStock.lock();
        try {
            List<InventarioSnapshot.ExistenciaProductoSnapshot> lista = new ArrayList<>();
            for (Producto p : CatalogoProductos.obtenerTodos()) {
                int fisica = getExistenciaFisica(p.id());
                int res = getReservado(p.id());
                int disp = Math.max(0, fisica - res);
                lista.add(new InventarioSnapshot.ExistenciaProductoSnapshot(
                        p.id(), p.nombre(), p.tipoMercancia().name(), fisica, res, disp
                ));
            }
            return new InventarioSnapshot(
                    totalMensajesProcesados.get(),
                    lotesPendientesEspacio.size(),
                    Collections.unmodifiableList(lista)
            );
        } finally {
            lockStock.unlock();
        }
    }
}
