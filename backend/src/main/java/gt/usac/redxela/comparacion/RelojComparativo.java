package gt.usac.redxela.comparacion;

import gt.usac.redxela.config.SimClock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reloj lógico para trabajadores reales. El conductor sólo avanza cuando todos
 * duermen o terminaron: el orden de ejecución del SO no modifica los tiempos.
 */
final class RelojComparativo extends SimClock {
    private volatile long ahoraMs;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cambio = lock.newCondition();
    private final Map<Thread, Long> tiempoHilo = new HashMap<>();
    private final Map<Thread, Long> dormidos = new HashMap<>();

    RelojComparativo() { super(600); }

    @Override public long ahoraSimuladoMs() { return ahoraMs; }
    @Override public long ahoraSimuladoSegundos() { return ahoraMs / 1000; }

    /** Registrar antes de start evita confundir un hilo aún no iniciado con reposo. */
    void registrar(Thread hilo) {
        lock.lock();
        try { tiempoHilo.put(hilo, ahoraMs); }
        finally { lock.unlock(); }
    }

    /** Cada hilo acumula sus ticks propios, aunque el conductor salte varios ticks. */
    @Override public void dormirSimuladoMs(long duracionMs) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            Thread hilo = Thread.currentThread();
            long objetivo = tiempoHilo.get(hilo) + duracionMs;
            tiempoHilo.put(hilo, objetivo);
            while (ahoraMs < objetivo) {
                dormidos.put(hilo, objetivo);
                cambio.signalAll();
                cambio.await();
            }
            dormidos.remove(hilo);
        } finally { lock.unlock(); }
    }

    /** Se llama después de liberar recursos y registrar métricas, incluso ante fallo. */
    void finalizarHilo() {
        lock.lock();
        try {
            tiempoHilo.remove(Thread.currentThread());
            dormidos.remove(Thread.currentThread());
            cambio.signalAll();
        } finally { lock.unlock(); }
    }

    void avanzarHasta(long segundos) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            if (segundos * 1000 < ahoraMs) throw new IllegalArgumentException("El reloj no puede retroceder");
            ahoraMs = segundos * 1000;
            cambio.signalAll();
        } finally { lock.unlock(); }
        esperarReposo();
    }

    /** La condición evita espera activa; el plazo sólo detecta fallos, no mide trabajo. */
    void esperarReposo() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            long plazo = TimeUnit.SECONDS.toNanos(10);
            while (tiempoHilo.keySet().stream().anyMatch(h -> !dormidos.containsKey(h) || dormidos.get(h) <= ahoraMs)) {
                if (plazo <= 0) throw new IllegalStateException("Un trabajador del comparador no alcanzó el reposo");
                plazo = cambio.awaitNanos(plazo);
            }
        } finally { lock.unlock(); }
    }
}
