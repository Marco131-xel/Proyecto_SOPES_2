package gt.usac.redxela.api.dto;

/** Datos del comando HTTP; el controlador valida antes de modificar el motor. */
public record VelocidadRelojRequest(
        Double factor,
        Boolean pausar
) {}
