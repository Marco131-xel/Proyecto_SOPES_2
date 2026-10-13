package gt.usac.redxela.recursos;

import gt.usac.redxela.modelo.TipoRecurso;

/**
 * Representa una unidad física individual dentro de un pool de recursos
 * (por ejemplo, "Empaque-1", "Montacargas-2").
 * 
 * Permite a la interfaz de usuario identificar qué pedido específico tiene
 * asignada cada unidad en cualquier instante.
 * Su acceso concurrente está coordinado por el lock del PoolRecurso contenedor.
 */
public class UnidadRecurso {

    private final String id;
    private final String nombreLegible;
    private final TipoRecurso tipo;

    private String duenoActual; // ID del pedido ("PED-000001") o "MANTENIMIENTO" o null si libre
    private boolean enMantenimiento;
    private long instanteAsignacion; // Instante en segundos simulados

    public UnidadRecurso(String id, String nombreLegible, TipoRecurso tipo) {
        this.id = id;
        this.nombreLegible = nombreLegible;
        this.tipo = tipo;
        this.duenoActual = null;
        this.enMantenimiento = false;
        this.instanteAsignacion = 0;
    }

    public synchronized void asignar(String pedidoId, long instanteSimulado) {
        this.duenoActual = pedidoId;
        this.instanteAsignacion = instanteSimulado;
    }

    public synchronized void liberar() {
        if (!enMantenimiento) {
            this.duenoActual = null;
        }
        this.instanteAsignacion = 0;
    }

    public synchronized void setEnMantenimiento(boolean mantenimiento) {
        this.enMantenimiento = mantenimiento;
        if (mantenimiento) {
            this.duenoActual = "MANTENIMIENTO";
        } else {
            this.duenoActual = null;
        }
        this.instanteAsignacion = 0;
    }

    public synchronized boolean isDisponible() {
        return !enMantenimiento && duenoActual == null;
    }

    public synchronized boolean isEnMantenimiento() {
        return enMantenimiento;
    }

    public synchronized String getDuenoActual() {
        return duenoActual;
    }

    public synchronized long getInstanteAsignacion() {
        return instanteAsignacion;
    }

    public String getId() {
        return id;
    }

    public String getNombreLegible() {
        return nombreLegible;
    }

    public TipoRecurso getTipo() {
        return tipo;
    }

    public synchronized UnidadSnapshot snapshot() {
        return new UnidadSnapshot(id, nombreLegible, tipo, duenoActual, enMantenimiento, instanteAsignacion);
    }

    public record UnidadSnapshot(
            String id,
            String nombreLegible,
            TipoRecurso tipo,
            String duenoActual,
            boolean enMantenimiento,
            long instanteAsignacion
    ) {}
}
