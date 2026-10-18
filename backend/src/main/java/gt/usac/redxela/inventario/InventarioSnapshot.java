package gt.usac.redxela.inventario;

import java.util.List;

/**
 * Snapshot inmutable del estado del Inventario (R16).
 */
public record InventarioSnapshot(
        long totalMensajesProcesados,
        int lotesPendientesEspacio,
        List<ExistenciaProductoSnapshot> existencias
) {
    public record ExistenciaProductoSnapshot(
            String productoId,
            String productoNombre,
            String tipoMercancia,
            int existenciasFisicas,
            int reservado,
            int disponibleParaPedidos
    ) {}
}
