package gt.usac.redxela.proceso;

import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.modelo.Pedido;
import gt.usac.redxela.modelo.TipoRecurso;
import gt.usac.redxela.recursos.Concesion;
import gt.usac.redxela.recursos.GestorRecursos;
import gt.usac.redxela.recursos.UnidadRecurso;

import java.util.Objects;

/**
 * Hilo de procesamiento individual de un pedido (Worker Thread).
 * 
 * Cada pedido activo se ejecuta de manera independiente en su propio hilo de plataforma,
 * modelando un proceso del sistema operativo que compite por recursos, realiza trabajo
 * proporcional a su tamaño (R6) y libera sus recursos en un bloque finally (RAII)
 * para evitar fugas de recursos (resource leaks).
 */
public class TrabajadorPedido implements Runnable {

    private final Pedido pedido;
    private final GestorRecursos gestorRecursos;
    private final SimClock simClock;
    private final Concesion concesionPrevia; // No nula si vino pre-adquirida en modo con prevención
    private final Runnable onFinalizado;

    public TrabajadorPedido(Pedido pedido, GestorRecursos gestorRecursos, SimClock simClock,
                            Concesion concesionPrevia, Runnable onFinalizado) {
        this.pedido = Objects.requireNonNull(pedido, "El pedido no puede ser nulo");
        this.gestorRecursos = Objects.requireNonNull(gestorRecursos, "El gestor de recursos no puede ser nulo");
        this.simClock = Objects.requireNonNull(simClock, "El reloj de simulación no puede ser nulo");
        this.concesionPrevia = concesionPrevia;
        this.onFinalizado = onFinalizado;
    }

    @Override
    public void run() {
        Concesion concesionEfectiva = this.concesionPrevia;

        try {
            // MODO SIN PREVENCIÓN: Si no se pre-adquirió de forma atómica,
            // el trabajador adquiere los recursos uno a uno reteniendo los ya obtenidos (Hold and Wait)
            if (concesionEfectiva == null) {
                concesionEfectiva = new Concesion(pedido.getId());
                pedido.marcarEnEsperaRecursos();

                for (TipoRecurso tipo : pedido.getOrdenAdquisicion()) {
                    // Pausa entre adquisiciones para permitir la contención y reproducibilidad de deadlocks
                    Thread.sleep(ConfiguracionSimulacion.PAUSA_ENTRE_ADQUISICIONES_MS);

                    UnidadRecurso unidad = gestorRecursos.adquirirUnoAUno(
                            tipo, pedido.getId(), simClock.ahoraSimuladoSegundos());
                    concesionEfectiva.agregar(gestorRecursos.getPool(tipo), unidad);
                    pedido.registrarUnidadRetenida(unidad.getNombreLegible());
                }
            }

            // Transición a EN_PROCESO
            long instanteInicio = simClock.ahoraSimuladoSegundos();
            pedido.iniciarProcesamiento(instanteInicio);

            // Trabajo simulado proporcional a las unidades totales del pedido (R6)
            long duracionTotalSimMs = pedido.getDuracionEstimadaSegundos() * 1000L;
            long pasoSimMs = Math.max(500L, duracionTotalSimMs / 20); // 20 intervalos de actualización
            long acumuladoSimMs = 0;

            while (acumuladoSimMs < duracionTotalSimMs) {
                long dormirSimMs = Math.min(pasoSimMs, duracionTotalSimMs - acumuladoSimMs);
                simClock.dormirSimuladoMs(dormirSimMs);
                acumuladoSimMs += dormirSimMs;

                double porcentaje = Math.min(100.0, ((double) acumuladoSimMs / duracionTotalSimMs) * 100.0);
                pedido.actualizarProgreso(porcentaje);
            }

            // Proceso completado exitosamente
            long instanteFin = simClock.ahoraSimuladoSegundos();
            pedido.completarProcesamiento(instanteFin);

        } catch (InterruptedException e) {
            // Interrupción recibida (por ejemplo, elección de víctima en resolución de interbloqueo)
            pedido.incrementarReencolado();
            pedido.marcarEnEsperaRecursos();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.printf("[ERROR] Fallo inesperado en hilo de pedido %s: %s%n", pedido.getId(), e.getMessage());
            pedido.cancelar();
        } finally {
            // Garantía estricta de liberación incondicional de recursos retenidos
            if (concesionEfectiva != null) {
                concesionEfectiva.close();
            }
            pedido.limpiarUnidadesRetenidas();

            if (onFinalizado != null) {
                onFinalizado.run();
            }
        }
    }

    public Pedido getPedido() {
        return pedido;
    }
}
