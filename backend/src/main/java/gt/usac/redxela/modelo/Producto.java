package gt.usac.redxela.modelo;

import java.util.Objects;

/**
 * Representa un producto del catálogo predefinido de REDXela.
 * 
 * Cada producto pertenece a un TipoMercancia específico y define su factor
 * de empaquetamiento en almacén: 'unidadesPorUbicacion', indicando cuántas
 * unidades máximas caben en una única celda física del almacén.
 */
public record Producto(
        String id,
        String nombre,
        TipoMercancia tipoMercancia,
        int unidadesPorUbicacion
) {
    public Producto {
        Objects.requireNonNull(id, "El ID del producto no puede ser nulo");
        Objects.requireNonNull(nombre, "El nombre del producto no puede ser nulo");
        Objects.requireNonNull(tipoMercancia, "El tipo de mercancía no puede ser nulo");
        if (unidadesPorUbicacion <= 0) {
            throw new IllegalArgumentException("Las unidades por ubicación deben ser mayores a 0");
        }
    }
}
