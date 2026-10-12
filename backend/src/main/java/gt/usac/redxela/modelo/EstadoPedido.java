package gt.usac.redxela.modelo;

/**
 * Representa los estados del ciclo de vida de un pedido en REDXela.
 * 
 * Modela la máquina de estados de un proceso en el sistema operativo:
 * - EN_ESPERA_STOCK: Análogo a estado BLOQUEADO por E/S (esperando llegada de páginas/datos).
 * - EN_ESPERA_RECURSOS: Análogo a estado LISTO (READY) en la cola de planificación.
 * - EN_PROCESO: Análogo a estado EN EJECUCIÓN (RUNNING) con CPU y recursos asignados.
 * - COMPLETADO: Análogo a estado TERMINADO (TERMINATED) con éxito.
 * - BLOQUEADO: Estado de INTERBLOQUEO (DEADLOCK) circular en espera de recursos retenidos.
 * - CANCELADO: Terminado anormalmente o abortado por usuario.
 */
public enum EstadoPedido {
    EN_ESPERA_STOCK("En espera de stock"),
    EN_ESPERA_RECURSOS("En espera de recursos"),
    EN_PROCESO("En proceso"),
    COMPLETADO("Completado"),
    BLOQUEADO("Interbloqueado"),
    CANCELADO("Cancelado");

    private final String descripcion;

    EstadoPedido(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
