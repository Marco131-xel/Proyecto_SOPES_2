package gt.usac.redxela.almacen;

/**
 * Entrada en la tabla de asignación indexada de un lote.
 * 
 * Modela un puntero directo a un bloque físico: asocia una celda física
 * del almacén con la cantidad de unidades alojadas en ella.
 */
public record EntradaAsignacion(
        Ubicacion ubicacion,
        int unidadesAlmacenadas
) {}
