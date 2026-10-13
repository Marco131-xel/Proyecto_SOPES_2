package gt.usac.redxela.recursos;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Representa el conjunto de unidades de recursos retenidas por un proceso (pedido).
 * 
 * Implementa AutoCloseable (patrón RAII / Try-with-resources):
 * Al invocarse close(), garantiza la liberación atómica e incondicional de todas las
 * unidades retenidas devolviendo los permisos a sus respectivos semáforos contadores.
 * 
 * Esto erradica fugas de recursos (resource leaks) ante excepciones imprevistas
 * o cuando el hilo es interrumpido por el algoritmo de resolución de interbloqueos.
 */
public class Concesion implements AutoCloseable {

    public record Asignacion(PoolRecurso pool, UnidadRecurso unidad) {}

    private final String pedidoId;
    private final List<Asignacion> asignaciones = new ArrayList<>();
    private final AtomicBoolean liberada = new AtomicBoolean(false);

    public Concesion(String pedidoId) {
        this.pedidoId = Objects.requireNonNull(pedidoId, "El ID del pedido no puede ser nulo");
    }

    public synchronized void agregar(PoolRecurso pool, UnidadRecurso unidad) {
        if (liberada.get()) {
            throw new IllegalStateException("No se pueden agregar recursos a una concesión ya cerrada");
        }
        asignaciones.add(new Asignacion(pool, unidad));
    }

    public synchronized List<Asignacion> getAsignaciones() {
        return Collections.unmodifiableList(new ArrayList<>(asignaciones));
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public boolean isLiberada() {
        return liberada.get();
    }

    @Override
    public void close() {
        if (liberada.compareAndSet(false, true)) {
            synchronized (this) {
                // Liberamos en orden inverso de adquisición (LIFO) para buen orden de desbloqueo
                for (int i = asignaciones.size() - 1; i >= 0; i--) {
                    Asignacion a = asignaciones.get(i);
                    try {
                        a.pool().release(a.unidad(), pedidoId);
                    } catch (Exception e) {
                        System.err.printf("[ERROR] Fallo al liberar unidad %s para pedido %s: %s%n",
                                a.unidad().getId(), pedidoId, e.getMessage());
                    }
                }
                asignaciones.clear();
            }
        }
    }
}
