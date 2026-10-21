package gt.usac.redxela.api.dto;

import java.util.List;

/** Datos del comando HTTP; el controlador valida antes de modificar el motor. */
public record CrearPedidoRequest(
        String clienteId,
        Integer nivelServicio, // Opcional, hereda del cliente si es nulo
        List<LineaDTO> lineas,
        List<String> ordenAdquisicionPersonalizado // Opcional para pruebas avanzadas
) {
    public record LineaDTO(
            String productoId,
            int cantidad
    ) {}
}
