package gt.usac.redxela.deadlock;

/**
 * Modela un arco o arista dentro del Grafo de Asignación de Recursos (RAG - Resource Allocation Graph).
 * 
 * En teoría de Sistemas Operativos (Holt, 1972):
 * - RETIENE: Arista dirigida Recurso -> Proceso (el proceso tiene asignada la unidad).
 * - SOLICITA: Arista dirigida Proceso -> Recurso (el proceso está bloqueado esperando la unidad).
 * 
 * La presencia de un ciclo dirigido en un grafo con recursos de instancia única
 * constituye condición necesaria y suficiente de interbloqueo.
 */
public record AristaAsignacion(
        String nodoProceso,
        String nodoRecurso,
        TipoArista tipoArista
) {
    public enum TipoArista {
        RETIENE,   // Recurso asignado al proceso
        SOLICITUD  // Proceso bloqueado esperando el recurso
    }
}
