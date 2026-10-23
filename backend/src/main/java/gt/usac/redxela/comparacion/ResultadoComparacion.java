package gt.usac.redxela.comparacion;

import gt.usac.redxela.estadisticas.EstadisticasSnapshot;
import gt.usac.redxela.modelo.TipoRecurso;
import java.util.List;
import java.util.Map;

/** Valores inmutables del experimento, independientes de fecha real y estado en vivo. */
public record ResultadoComparacion(
        ConfiguracionComparacion configuracion, String huellaCarga, String alcance,
        List<ResultadoPolitica> politicas, String conclusion
) {
    public ResultadoComparacion { politicas = List.copyOf(politicas); }

    public record ResultadoPolitica(
            String politica, String descripcion, int pedidosCompletados, long horizonteSimuladoSegundos,
            double esperaPromedioSegundos, long esperaMaximaSegundos, double porcentajeSLA,
            double throughputPedidosPorMinuto, double ocupacionMediaPorcentaje, long pedidosConInanicion,
            List<EstadisticasSnapshot.EstadisticaNivelSnapshot> niveles,
            Map<TipoRecurso, Double> ocupacionPorRecurso
    ) {
        public ResultadoPolitica {
            niveles = List.copyOf(niveles);
            ocupacionPorRecurso = Map.copyOf(ocupacionPorRecurso);
        }
    }
}
