package gt.usac.redxela.modelo;

import java.util.Objects;

/**
 * Representa una cuenta de cliente en REDXela (R1).
 * 
 * Cada cliente posee un nivel de servicio contratado (1 a 5).
 * Los pedidos emitidos por el cliente heredan por defecto este nivel.
 */
public record Cliente(
        String id,
        String nombre,
        String correo,
        NivelServicio nivelContratado
) {
    public Cliente {
        Objects.requireNonNull(id, "El ID de cliente no puede ser nulo");
        Objects.requireNonNull(nombre, "El nombre de cliente no puede ser nulo");
        Objects.requireNonNull(correo, "El correo de cliente no puede ser nulo");
        Objects.requireNonNull(nivelContratado, "El nivel contratado no puede ser nulo");
    }
}
