package gt.usac.redxela.recepcion;

import java.util.List;

/**
 * Snapshot inmutable del estado del área de Recepción (R16).
 */
public record RecepcionSnapshot(
        int camionesEnPatio,
        int mensajesEnColaRecepcion,
        int capacidadMaximaCola,
        long camionesProcesadosTotal,
        boolean escanerOcupado,
        List<Camion> camionesEnPatioDetalle
) {}
