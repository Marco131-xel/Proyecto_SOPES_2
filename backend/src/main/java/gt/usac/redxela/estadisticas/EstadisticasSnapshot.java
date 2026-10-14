package gt.usac.redxela.estadisticas;

import java.util.List;

/**
 * Instantánea inmutable (Snapshot DTO) que contiene todas las métricas
 * de rendimiento agregadas del centro de distribución REDXela (R16).
 */
public record EstadisticasSnapshot(
        long pedidosCreados,
        long pedidosCompletados,
        long pedidosReencolados,
        int pedidosEnColaActuales,
        int pedidosEnProcesoActuales,
        double esperaPromedioGlobalSegundos,
        double procesamientoPromedioGlobalSegundos,
        double porcentajeCumplimientoSLAGlobal,
        double throughputPedidosPorMinutoSim,
        List<EstadisticaNivelSnapshot> metricasPorNivel,
        List<MuestraHistoricaCola> serieHistoricaCola
) {
    public record EstadisticaNivelSnapshot(
            int nivel,
            String nombreNivel,
            long completados,
            double esperaPromedioSegundos,
            long esperaMaximaSegundos,
            double procesamientoPromedioSegundos,
            long cumplieronSLA,
            long fallaronSLA,
            double porcentajeCumplimientoSLA
    ) {}
}
