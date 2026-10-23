package gt.usac.redxela.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Reloj de simulación acelerado para el sistema REDXela.
 * 
 * Permite que los procesos del centro de distribución operen en una escala
 * temporal acelerada (por ejemplo, 1 segundo real = 1 minuto simulado) de modo
 * que los tiempos objetivo de SLA (hasta 120 minutos) puedan demostrarse y
 * observarse en lapsos razonables en la interfaz gráfica.
 * 
 * Garantiza continuidad matemática monótona: los cambios de velocidad o pausas
 * recalculan los anclajes de tiempo base sin producir saltos temporales abruptos.
 */
@Component
public class SimClock {

    private final ReentrantLock lock = new ReentrantLock();

    // Factor de aceleración: 1 ms real equivale a (factor) ms simulados
    private double factor;

    // Anclajes para el cálculo de la función lineal de tiempo continuo
    private long tiempoRealBaseMs;
    private long tiempoSimuladoBaseMs;
    private boolean pausado;

    /**
     * Inicializa el reloj con el factor por defecto configurado en el sistema.
     */
    public SimClock() {
        this(ConfiguracionSimulacion.FACTOR_RELOJ_POR_DEFECTO);
    }

    /**
     * Inicializa el reloj con un factor de aceleración específico.
     * 
     * @param factorInicial Multiplicador de velocidad (ej. 60.0 = 1s real son 60s sim).
     */
    public SimClock(double factorInicial) {
        if (!Double.isFinite(factorInicial)) throw new IllegalArgumentException("Factor no finito");
        long ahoraReal = System.nanoTime() / 1_000_000L;
        this.factor = Math.clamp(factorInicial, ConfiguracionSimulacion.FACTOR_RELOJ_MINIMO, ConfiguracionSimulacion.FACTOR_RELOJ_MAXIMO);
        this.tiempoRealBaseMs = ahoraReal;
        this.tiempoSimuladoBaseMs = 0;
        this.pausado = false;
    }

    /**
     * Obtiene el tiempo simulado actual en milisegundos desde el arranque.
     * 
     * @return Milisegundos simulados transcurridos.
     */
    public long ahoraSimuladoMs() {
        lock.lock();
        try {
            if (pausado) {
                return tiempoSimuladoBaseMs;
            }
            long deltaReal = System.nanoTime() / 1_000_000L - tiempoRealBaseMs;
            return tiempoSimuladoBaseMs + (long) (deltaReal * factor);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Obtiene el tiempo simulado actual en segundos.
     * 
     * @return Segundos simulados transcurridos.
     */
    public long ahoraSimuladoSegundos() {
        return ahoraSimuladoMs() / 1000L;
    }

    /**
     * Modifica el factor de aceleración del reloj en caliente.
     * Recalcula el anclaje base para garantizar que el tiempo simulado no sufra saltos.
     * 
     * @param nuevoFactor Nuevo factor entre [1.0, 600.0].
     */
    public void setFactor(double nuevoFactor) {
        if (!Double.isFinite(nuevoFactor)) throw new IllegalArgumentException("Factor no finito");
        lock.lock();
        try {
            long simActual = ahoraSimuladoMs();
            long realActual = System.nanoTime() / 1_000_000L;

            this.factor = Math.clamp(nuevoFactor, ConfiguracionSimulacion.FACTOR_RELOJ_MINIMO, ConfiguracionSimulacion.FACTOR_RELOJ_MAXIMO);
            this.tiempoSimuladoBaseMs = simActual;
            this.tiempoRealBaseMs = realActual;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Pausa el reloj de la simulación.
     */
    public void pausar() {
        lock.lock();
        try {
            if (!pausado) {
                this.tiempoSimuladoBaseMs = ahoraSimuladoMs();
                this.pausado = true;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reanuda la marcha del reloj tras una pausa.
     */
    public void reanudar() {
        lock.lock();
        try {
            if (pausado) {
                this.tiempoRealBaseMs = System.nanoTime() / 1_000_000L;
                this.pausado = false;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Duerme el hilo llamador por una duración expresada en milisegundos simulados.
     * Convierte la duración simulada a tiempo real de acuerdo al factor actual.
     * 
     * @param duracionSimuladaMs Duración simulada que debe esperar el proceso.
     * @throws InterruptedException si el hilo es interrumpido mientras duerme.
     */
    public void dormirSimuladoMs(long duracionSimuladaMs) throws InterruptedException {
        if (duracionSimuladaMs <= 0) {
            return;
        }
        // Esperar contra el reloj simulado hace que pausa y cambios de factor
        // afecten también al trabajo en curso, no sólo a las próximas esperas.
        long objetivo = ahoraSimuladoMs() + duracionSimuladaMs;
        while (true) {
            long restante = objetivo - ahoraSimuladoMs();
            if (restante <= 0) return;
            long esperaReal = isPausado() ? 100L : Math.max(1L, (long) Math.ceil(restante / getFactor()));
            Thread.sleep(Math.min(100L, esperaReal));
        }
    }

    /**
     * Duerme el hilo llamador por una duración expresada en segundos simulados.
     * 
     * @param duracionSimuladaSegundos Duración simulada en segundos.
     * @throws InterruptedException si el hilo es interrumpido.
     */
    public void dormirSimuladoSegundos(long duracionSimuladaSegundos) throws InterruptedException {
        dormirSimuladoMs(duracionSimuladaSegundos * 1000L);
    }

    public double getFactor() {
        lock.lock();
        try {
            return factor;
        } finally {
            lock.unlock();
        }
    }

    public boolean isPausado() {
        lock.lock();
        try {
            return pausado;
        } finally {
            lock.unlock();
        }
    }
}
