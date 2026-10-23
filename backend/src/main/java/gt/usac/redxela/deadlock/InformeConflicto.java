package gt.usac.redxela.deadlock;

import java.util.Collections;
import java.util.List;

/**
 * Informe detallado de interbloqueo emitido por el DetectorDeadlock (R14).
 * 
 * Expone los procesos bloqueados entre sí, los recursos disputados, la explicación
 * técnica en lenguaje natural y la topología del Grafo de Asignación de Recursos (RAG)
 * para ser graficada en la interfaz de usuario.
 */
public record InformeConflicto(
        boolean hayConflicto,
        List<String> pedidosInvolucrados,
        List<String> recursosDisputados,
        List<AristaAsignacion> grafoAsignacion,
        String explicacion,
        long instanteDeteccion
) {
    public InformeConflicto {
        pedidosInvolucrados = List.copyOf(pedidosInvolucrados);
        recursosDisputados = List.copyOf(recursosDisputados);
        grafoAsignacion = List.copyOf(grafoAsignacion);
    }

    /** Cero indica que no hay instante de detección de conflicto aplicable. */
    public static InformeConflicto sinConflicto() {
        return new InformeConflicto(
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "No se detecta ningún interbloqueo en el sistema. Todos los procesos avanzan normalmente.",
                0L
        );
    }
}
