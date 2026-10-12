package gt.usac.redxela.modelo;

import java.util.Objects;

/**
 * Representa una línea de detalle dentro de un pedido.
 * 
 * Asocia un producto del catálogo con la cantidad requerida de unidades.
 */
public record LineaPedido(
        Producto producto,
        int cantidad
) {
    public LineaPedido {
        Objects.requireNonNull(producto, "El producto de la línea no puede ser nulo");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0: " + cantidad);
        }
    }
}
