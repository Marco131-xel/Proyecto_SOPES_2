package gt.usac.redxela.api.dto;

/** Datos del comando HTTP; el controlador valida antes de modificar el motor. */
public record CrearClienteRequest(
        String nombre,
        String correo,
        int nivelServicio // 1 a 5
) {}
