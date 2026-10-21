package gt.usac.redxela.api.dto;

/** Datos del comando HTTP; el controlador valida antes de modificar el motor. */
public record CambiarPoliticaRequest(
        String politica // "PRIORIDAD_ESTRICTA", "PRIORIDAD_CON_AGING", "MENOR_CANTIDAD_PRIMERO", "EDF"
) {}
