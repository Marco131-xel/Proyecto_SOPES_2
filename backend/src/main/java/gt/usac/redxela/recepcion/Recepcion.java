package gt.usac.redxela.recepcion;

import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.modelo.Producto;
import gt.usac.redxela.modelo.TipoRecurso;
import gt.usac.redxela.recursos.GestorRecursos;
import gt.usac.redxela.recursos.PoolRecurso;
import gt.usac.redxela.recursos.UnidadRecurso;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Módulo de Recepción de Mercancía (Productor del patrón Productor-Consumidor / R10).
 * 
 * Funciones clave:
 * 1. COLA EN PATIO Y PETICIONES HTTP NO BLOQUEANTES:
 *    Las peticiones REST solo encolan el camión en el patio y retornan 202 de inmediato.
 * 
 * 2. EXCLUSIÓN MUTUA DEL ESCÁNER:
 *    El escáner es un recurso único (capacidad 1) compartido con Despacho. Antes de
 *    emitir el mensaje a Inventario, el hilo de Recepción adquiere el escáner,
 *    simula la lectura de código de barras y libera el escáner.
 * 
 * 3. PASO DE MENSAJES Y CONTRAPRESIÓN (Backpressure):
 *    Emite un MensajeRecepcion inmutable a una ArrayBlockingQueue acotada (capacidad 20).
 *    Si Inventario es más lento, Recepción se bloquea en put(); los camiones esperan
 *    en el patio y no se pierde ningún dato ni se satura la memoria (R10).
 */
@Component
public class Recepcion implements Runnable {

    private final GestorRecursos gestorRecursos;
    private final SimClock simClock;

    private final ArrayBlockingQueue<MensajeRecepcion> colaMensajes =
            new ArrayBlockingQueue<>(ConfiguracionSimulacion.CAPACIDAD_COLA_RECEPCION);

    private final Deque<Camion> patioCamiones = new ConcurrentLinkedDeque<>();
    private final ReentrantLock lockPatio = new ReentrantLock();
    private final Condition hayCamionesEnPatio = lockPatio.newCondition();

    private final AtomicLong secuencialCamiones = new AtomicLong(0);
    private final AtomicLong camionesProcesados = new AtomicLong(0);
    private final AtomicBoolean activo = new AtomicBoolean(false);

    private Thread hiloRecepcion;

    public Recepcion(GestorRecursos gestorRecursos, SimClock simClock) {
        this.gestorRecursos = gestorRecursos;
        this.simClock = simClock;
    }

    public synchronized void iniciar() {
        if (!activo.get()) {
            activo.set(true);
            hiloRecepcion = new Thread(this, "hilo-recepcion");
            hiloRecepcion.setDaemon(true);
            hiloRecepcion.start();
        }
    }

    public synchronized void detener() {
        activo.set(false);
        lockPatio.lock();
        try {
            hayCamionesEnPatio.signalAll();
        } finally {
            lockPatio.unlock();
        }
        if (hiloRecepcion != null) {
            hiloRecepcion.interrupt();
        }
    }

    /**
     * Registra la llegada de un camión al patio. Método no bloqueante para peticiones HTTP.
     * 
     * @param producto Producto transportado.
     * @param cantidad Unidades de producto.
     * @return Camión creado y encolado en patio.
     */
    public Camion registrarLlegada(Producto producto, int cantidad) {
        String id = String.format("CAM-%04d", secuencialCamiones.incrementAndGet());
        long ahoraSim = simClock.ahoraSimuladoSegundos();
        Camion camion = new Camion(id, producto, cantidad, ahoraSim);

        lockPatio.lock();
        try {
            patioCamiones.addLast(camion);
            hayCamionesEnPatio.signalAll();
        } finally {
            lockPatio.unlock();
        }

        return camion;
    }

    @Override
    public void run() {
        PoolRecurso poolEscaner = gestorRecursos.getPool(TipoRecurso.ESCANER);

        while (activo.get() && !Thread.currentThread().isInterrupted()) {
            Camion camion = null;
            try {
                lockPatio.lock();
                try {
                    while (patioCamiones.isEmpty() && activo.get()) {
                        hayCamionesEnPatio.await();
                    }
                    if (!activo.get()) break;
                    camion = patioCamiones.pollFirst();
                } finally {
                    lockPatio.unlock();
                }

                if (camion == null) continue;

                // 1. Adquisición del escáner en exclusión mutua
                long ahoraSim = simClock.ahoraSimuladoSegundos();
                UnidadRecurso escaner = poolEscaner.acquire(camion.id(), ahoraSim);

                try {
                    // Simular tiempo de escaneo del camión
                    simClock.dormirSimuladoMs(200);
                } finally {
                    // Liberación inmediata del escáner para que otras áreas puedan usarlo
                    poolEscaner.release(escaner, camion.id());
                }

                // 2. Construcción del mensaje inmutable (R10)
                MensajeRecepcion mensaje = new MensajeRecepcion(
                        camion.id(),
                        camion.producto().id(),
                        camion.cantidad(),
                        simClock.ahoraSimuladoSegundos()
                );

                // 3. Envío bloqueante a la cola acotada (aplica contrapresión si está llena)
                colaMensajes.put(mensaje);
                camionesProcesados.incrementAndGet();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.printf("[ERROR] Fallo en hilo de recepción: %s%n", e.getMessage());
            }
        }
    }

    public ArrayBlockingQueue<MensajeRecepcion> getColaMensajes() {
        return colaMensajes;
    }

    public RecepcionSnapshot snapshot() {
        PoolRecurso poolEscaner = gestorRecursos.getPool(TipoRecurso.ESCANER);
        boolean escanerEnUso = poolEscaner.getPermisosDisponibles() == 0;

        return new RecepcionSnapshot(
                patioCamiones.size(),
                colaMensajes.size(),
                ConfiguracionSimulacion.CAPACIDAD_COLA_RECEPCION,
                camionesProcesados.get(),
                escanerEnUso,
                new ArrayList<>(patioCamiones)
        );
    }
}
