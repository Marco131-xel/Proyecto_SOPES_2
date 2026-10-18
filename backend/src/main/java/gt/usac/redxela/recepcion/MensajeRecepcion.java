package gt.usac.redxela.recepcion;

import java.util.Objects;

/**
 * Mensaje inmutable transmitido entre el proceso de Recepción y el de Inventario (R10).
 * 
 * Cumple estrictamente con el principio de paso de mensajes sin memoria compartida
 * (Message Passing IPC): Recepción e Inventario no comparten estructuras internas;
 * toda la comunicación de llegada de mercancía viaja a través de estos mensajes
 * inmutables sobre una cola acotada.
 */
public record MensajeRecepcion(
        String camionId,
        String productoId,
        int cantidad,
        long instanteSimulado
) {
    public MensajeRecepcion {
        Objects.requireNonNull(camionId, "camionId no puede ser nulo");
        Objects.requireNonNull(productoId, "productoId no puede ser nulo");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
    }
}
