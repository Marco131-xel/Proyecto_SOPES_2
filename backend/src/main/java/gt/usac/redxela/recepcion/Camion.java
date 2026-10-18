package gt.usac.redxela.recepcion;

import gt.usac.redxela.modelo.Producto;

import java.util.Objects;

/**
 * Representa un camión de suministro que arriba al patio de maniobras de REDXela.
 * 
 * Cada camión transporta una cantidad específica de un producto del catálogo (R11).
 */
public record Camion(
        String id,
        Producto producto,
        int cantidad,
        long instanteLlegada
) {
    public Camion {
        Objects.requireNonNull(id, "El ID del camión no puede ser nulo");
        Objects.requireNonNull(producto, "El producto no puede ser nulo");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
    }
}
