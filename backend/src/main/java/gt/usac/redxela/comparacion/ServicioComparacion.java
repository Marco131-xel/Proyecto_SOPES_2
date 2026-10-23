package gt.usac.redxela.comparacion;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Un experimento a la vez: no bloquea HTTP ni acumula solicitudes en una cola ilimitada. */
@Service
public class ServicioComparacion implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ServicioComparacion.class);
    private final SimuladorComparativo simulador;
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hilo-comparacion-politicas");
        t.setDaemon(true);
        return t;
    });
    private volatile EstadoComparacion estado = new EstadoComparacion(0, "SIN_EJECUTAR", null, null, null);
    private long secuencia;

    public ServicioComparacion() { this(new SimuladorComparativo()); }

    /** Permite probar errores y exclusión de trabajos sin depender del transporte. */
    ServicioComparacion(SimuladorComparativo simulador) { this.simulador = simulador; }

    public record EstadoComparacion(long id, String estado, ConfiguracionComparacion configuracion,
                                    ResultadoComparacion resultado, String error) {}

    public EstadoComparacion obtenerEstado() { return estado; }

    /** El monitor serializa la admisión antes de enviar el trabajo al ejecutor. */
    public synchronized EstadoComparacion iniciar(ConfiguracionComparacion configuracion) {
        if (ejecutor.isShutdown()) throw new IllegalStateException("El comparador está cerrado");
        if (estado.estado().equals("EJECUTANDO")) throw new IllegalStateException("Ya hay una comparación en ejecución");
        long id = ++secuencia;
        EstadoComparacion aceptado = new EstadoComparacion(id, "EJECUTANDO", configuracion, null, null);
        estado = aceptado;
        ejecutor.execute(() -> {
            try {
                var resultado = simulador.ejecutar(configuracion);
                estado = new EstadoComparacion(id, "COMPLETADA", configuracion, resultado, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                estado = new EstadoComparacion(id, "ERROR", configuracion, null, "Comparación interrumpida");
            } catch (Exception e) {
                log.error("Falló la comparación {}", id, e);
                estado = new EstadoComparacion(id, "ERROR", configuracion, null, "No se pudo completar la comparación; revise el registro del backend");
            }
        });
        return aceptado;
    }

    @Override @PreDestroy
    public synchronized void close() { ejecutor.shutdownNow(); }
}
