package gt.usac.redxela.api.dto;

import gt.usac.redxela.almacen.AlmacenSnapshot;
import gt.usac.redxela.deadlock.InformeConflicto;
import gt.usac.redxela.estadisticas.EstadisticasSnapshot;
import gt.usac.redxela.inventario.InventarioSnapshot;
import gt.usac.redxela.modelo.TipoRecurso;
import gt.usac.redxela.planificacion.ListaEspera;
import gt.usac.redxela.recepcion.RecepcionSnapshot;
import gt.usac.redxela.recursos.PoolRecurso;

import java.util.List;
import java.util.Map;

/**
 * DTO consolidado inmutable que representa la fotografía completa del centro
 * de distribución REDXela transmitida por SSE cada 500 ms (R16).
 */
public record EstadoSistema(
        long tiempoSimuladoSegundos,
        double factorReloj,
        boolean relojPausado,
        String politicaActiva,
        String descripcionPolitica,
        boolean prevencionDeadlock,
        boolean generadorActivo,
        boolean generadorPausado,
        double tasaGenerador,
        Map<TipoRecurso, PoolRecurso.PoolSnapshot> recursos,
        List<PedidoDetalleDTO> pedidosEnProceso,
        List<ListaEspera.PedidoSnapshot> listaEspera,
        List<PedidoDetalleDTO> ultimosCompletados,
        AlmacenSnapshot almacen,
        RecepcionSnapshot recepcion,
        InventarioSnapshot inventario,
        EstadisticasSnapshot estadisticas,
        InformeConflicto conflicto,
        List<gt.usac.redxela.eventos.RegistroEventos.Evento> eventos
) {
    public EstadoSistema {
        recursos = Map.copyOf(recursos);
        pedidosEnProceso = List.copyOf(pedidosEnProceso);
        listaEspera = List.copyOf(listaEspera);
        ultimosCompletados = List.copyOf(ultimosCompletados);
        eventos = List.copyOf(eventos);
    }
    public record PedidoDetalleDTO(
            String id,
            String clienteNombre,
            int nivelNumero,
            String nivelNombre,
            int totalUnidades,
            String estado,
            double progresoPorcentaje,
            long instanteInicio,
            long duracionEstimadaSegundos,
            long tiempoRestanteSegundos,
            List<String> recursosRetenidos,
            String recursoSolicitado,
            int vecesReencolado,
            List<String> tiposMercancia
    ) {
        public PedidoDetalleDTO {
            recursosRetenidos = List.copyOf(recursosRetenidos);
            tiposMercancia = List.copyOf(tiposMercancia);
        }
    }
}
