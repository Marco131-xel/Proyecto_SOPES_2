package gt.usac.redxela.almacen;

import java.util.List;

/**
 * Snapshot inmutable del estado físico del almacén (R16).
 * Alimenta la cuadrícula tipo mapa de calor y métricas de fragmentación en la UI.
 */
public record AlmacenSnapshot(
        int totalUbicaciones,
        int ubicacionesLibres,
        int ubicacionesOcupadas,
        double porcentajeOcupacion,
        int fragmentacionInternaTotalUnidades,
        int lotesPendientesEspacio,
        List<Ubicacion.UbicacionSnapshot> ubicaciones,
        List<Lote.LoteSnapshot> lotesActivos
) {
    public AlmacenSnapshot {
        ubicaciones = List.copyOf(ubicaciones);
        lotesActivos = List.copyOf(lotesActivos);
    }
}
