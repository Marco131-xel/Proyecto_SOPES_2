package gt.usac.redxela.recursos;

import gt.usac.redxela.modelo.TipoRecurso;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Administra un grupo de recursos homogéneos mediante un Semáforo Contador justo
 * (Semaphore FIFO) y un arreglo de unidades físicas individuales.
 * 
 * ¿Por qué Semaphore con fair=true?
 * En sistemas operativos, la justicia FIFO en los semáforos evita la inanición de hilos
 * que compiten por el mismo recurso, garantizando que el primer hilo en solicitar
 * un permiso sea el primero en recibirlo cuando esté disponible.
 * 
 * La asignación de qué unidad física específica (ej. "Montacargas-2") se otorga
 * a qué proceso se protege con un ReentrantLock dedicado de exclusión mutua.
 */
public class PoolRecurso {

    private final TipoRecurso tipo;
    private final int capacidadTotal;
    private final Semaphore semaforo;
    private final ReentrantLock lockUnidades = new ReentrantLock();
    private final List<UnidadRecurso> unidades;

    public PoolRecurso(TipoRecurso tipo, int capacidad) {
        this.tipo = Objects.requireNonNull(tipo, "El tipo de recurso no puede ser nulo");
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad del pool debe ser mayor a 0: " + capacidad);
        }
        this.capacidadTotal = capacidad;
        // Semáforo justo (FIFO) según principios de equidad del sistema operativo
        this.semaforo = new Semaphore(capacidad, true);

        List<UnidadRecurso> lista = new ArrayList<>(capacidad);
        for (int i = 1; i <= capacidad; i++) {
            String id = String.format("%s-%02d", tipo.getPrefijo(), i);
            String nombre = String.format("%s %d", tipo.getNombreLegible(), i);
            lista.add(new UnidadRecurso(id, nombre, tipo));
        }
        this.unidades = Collections.unmodifiableList(lista);
    }

    /**
     * Intenta adquirir una unidad del recurso de forma inmediata sin bloquear el hilo.
     * 
     * @param pedidoId ID del pedido solicitante.
     * @param instanteSimulado Instante actual en segundos simulados.
     * @return Optional con la unidad asignada, o Optional.empty() si no hay permisos disponibles.
     */
    public Optional<UnidadRecurso> tryAcquire(String pedidoId, long instanteSimulado) {
        if (!semaforo.tryAcquire()) {
            return Optional.empty();
        }

        lockUnidades.lock();
        try {
            for (UnidadRecurso u : unidades) {
                if (u.isDisponible()) {
                    u.asignar(pedidoId, instanteSimulado);
                    return Optional.of(u);
                }
            }
            // Estado anómalo de consistencia: si el semáforo dio permiso pero no hay unidad libre,
            // devolvemos el permiso al semáforo de inmediato para evitar fugas.
            semaforo.release();
            return Optional.empty();
        } finally {
            lockUnidades.unlock();
        }
    }

    /**
     * Adquiere una unidad del recurso bloqueando el hilo de forma interrumpible si no hay cupo.
     * 
     * @param pedidoId ID del pedido solicitante.
     * @param instanteSimulado Instante en segundos simulados.
     * @return La unidad física asignada.
     * @throws InterruptedException si el hilo es interrumpido mientras espera el semáforo.
     */
    public UnidadRecurso acquire(String pedidoId, long instanteSimulado) throws InterruptedException {
        semaforo.acquire(); // Bloqueo en la cola FIFO del semáforo

        lockUnidades.lock();
        try {
            for (UnidadRecurso u : unidades) {
                if (u.isDisponible()) {
                    u.asignar(pedidoId, instanteSimulado);
                    return u;
                }
            }
            // Si ocurriera inconsistencia inesperada, revertimos el permiso
            semaforo.release();
            throw new IllegalStateException("Semáforo otorgó permiso pero ninguna unidad estaba disponible en pool: " + tipo);
        } finally {
            lockUnidades.unlock();
        }
    }

    /**
     * Libera una unidad previamente concedida a un pedido y devuelve el permiso al semáforo.
     * 
     * @param unidad Unidad física a liberar.
     * @param pedidoId ID del pedido dueño.
     */
    public void release(UnidadRecurso unidad, String pedidoId) {
        if (unidad == null) {
            return;
        }

        lockUnidades.lock();
        try {
            if (pedidoId.equals(unidad.getDuenoActual())) {
                unidad.liberar();
                semaforo.release();
            }
        } finally {
            lockUnidades.unlock();
        }
    }

    /**
     * Pone una unidad en mantenimiento reduciendo los permisos del semáforo.
     * Útil para saturar recursos en la demostración controlada de interbloqueos (deadlock).
     * 
     * @param unidadId ID de la unidad (ej. "MTC-02").
     * @return true si se pudo bloquear en mantenimiento, false si no estaba libre o permiso falló.
     */
    public boolean ponerEnMantenimiento(String unidadId) {
        if (!semaforo.tryAcquire()) {
            return false;
        }

        lockUnidades.lock();
        try {
            for (UnidadRecurso u : unidades) {
                if (u.getId().equals(unidadId) && u.isDisponible()) {
                    u.setEnMantenimiento(true);
                    return true;
                }
            }
            // Si la unidad no existía o no estaba libre, revertimos el permiso
            semaforo.release();
            return false;
        } finally {
            lockUnidades.unlock();
        }
    }

    /**
     * Quita una unidad de mantenimiento y reintegra su permiso al semáforo.
     * 
     * @param unidadId ID de la unidad.
     * @return true si se retiró de mantenimiento exitosamente.
     */
    public boolean quitarMantenimiento(String unidadId) {
        lockUnidades.lock();
        try {
            for (UnidadRecurso u : unidades) {
                if (u.getId().equals(unidadId) && u.isEnMantenimiento()) {
                    u.setEnMantenimiento(false);
                    semaforo.release();
                    return true;
                }
            }
            return false;
        } finally {
            lockUnidades.unlock();
        }
    }

    public TipoRecurso getTipo() {
        return tipo;
    }

    public int getCapacidadTotal() {
        return capacidadTotal;
    }

    public int getPermisosDisponibles() {
        return semaforo.availablePermits();
    }

    public List<UnidadRecurso> getUnidades() {
        return unidades;
    }

    public PoolSnapshot snapshot() {
        lockUnidades.lock();
        try {
            List<UnidadRecurso.UnidadSnapshot> snapshots = new ArrayList<>();
            for (UnidadRecurso u : unidades) {
                snapshots.add(u.snapshot());
            }
            return new PoolSnapshot(tipo, capacidadTotal, semaforo.availablePermits(), snapshots);
        } finally {
            lockUnidades.unlock();
        }
    }

    public record PoolSnapshot(
            TipoRecurso tipo,
            int capacidadTotal,
            int disponibles,
            List<UnidadRecurso.UnidadSnapshot> unidades
    ) {}
}
