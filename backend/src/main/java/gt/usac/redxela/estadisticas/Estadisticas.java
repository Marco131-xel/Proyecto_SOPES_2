package gt.usac.redxela.estadisticas;

import gt.usac.redxela.modelo.NivelServicio;
import gt.usac.redxela.modelo.Pedido;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Módulo de agregación de estadísticas y métricas del centro de distribución (R16).
 * 
 * ¿Por qué LongAdder en lugar de AtomicLong?
 * En sistemas concurrentes de alta carga con múltiples hilos terminando pedidos
 * simultáneamente, AtomicLong sufre de contención severa por reintentos CAS
 * (Compare-And-Swap) sobre una única línea de caché. LongAdder distribuye internamente
 * la contención en celdas por hilo (striped counters), eliminando la degradación
 * por contención de bus de memoria y proveyendo escalabilidad lineal.
 */
@Component
public class Estadisticas {

    private final LongAdder pedidosCreados = new LongAdder();
    private final LongAdder pedidosCompletados = new LongAdder();
    private final LongAdder pedidosReencolados = new LongAdder();

    private final LongAdder sumaEsperaGlobalSegundos = new LongAdder();
    private final LongAdder sumaProcesamientoGlobalSegundos = new LongAdder();
    private final LongAdder cumplieronSLAGlobal = new LongAdder();

    // Arreglos indexados por nivel de servicio (1 a 5)
    private final LongAdder[] completadosPorNivel = new LongAdder[6];
    private final LongAdder[] sumaEsperaPorNivel = new LongAdder[6];
    private final AtomicLong[] maxEsperaPorNivel = new AtomicLong[6];
    private final LongAdder[] sumaProcesamientoPorNivel = new LongAdder[6];
    private final LongAdder[] cumplieronSLAPorNivel = new LongAdder[6];
    private final LongAdder[] fallaronSLAPorNivel = new LongAdder[6];

    private final Deque<MuestraHistoricaCola> historialCola = new ConcurrentLinkedDeque<>();
    private final AtomicLong primerInstanteSimulado = new AtomicLong(-1);

    public Estadisticas() {
        for (int i = 1; i <= 5; i++) {
            completadosPorNivel[i] = new LongAdder();
            sumaEsperaPorNivel[i] = new LongAdder();
            maxEsperaPorNivel[i] = new AtomicLong(0);
            sumaProcesamientoPorNivel[i] = new LongAdder();
            cumplieronSLAPorNivel[i] = new LongAdder();
            fallaronSLAPorNivel[i] = new LongAdder();
        }
    }

    public void registrarPedidoCreado(Pedido pedido) {
        pedidosCreados.increment();
        primerInstanteSimulado.compareAndSet(-1, pedido.getInstanteCreacion());
    }

    public void registrarReencolado() {
        pedidosReencolados.increment();
    }

    /**
     * Registra las métricas de un pedido completado y actualiza agregados y SLA.
     */
    public void registrarPedidoCompletado(Pedido pedido, long instanteFinSimulado) {
        pedidosCompletados.increment();

        int nivel = pedido.getNivel().getNivel();
        long inicioSim = pedido.getInstanteInicio() != null ? pedido.getInstanteInicio() : pedido.getInstanteCreacion();
        long esperaSegundos = Math.max(0, inicioSim - pedido.getInstanteCreacion());
        long procesamientoSegundos = Math.max(0, instanteFinSimulado - inicioSim);

        sumaEsperaGlobalSegundos.add(esperaSegundos);
        sumaProcesamientoGlobalSegundos.add(procesamientoSegundos);

        completadosPorNivel[nivel].increment();
        sumaEsperaPorNivel[nivel].add(esperaSegundos);
        maxEsperaPorNivel[nivel].accumulateAndGet(esperaSegundos, Math::max);
        sumaProcesamientoPorNivel[nivel].add(procesamientoSegundos);

        // Verificación estricta de SLA
        if (esperaSegundos <= pedido.getNivel().getEsperaObjetivoSegundos()) {
            cumplieronSLAGlobal.increment();
            cumplieronSLAPorNivel[nivel].increment();
        } else {
            fallaronSLAPorNivel[nivel].increment();
        }
    }

    /**
     * Registra una muestra temporal de longitud de cola para gráficos de la UI.
     */
    public void registrarMuestraCola(long instanteSimulado, int enCola, int enProceso) {
        historialCola.addLast(new MuestraHistoricaCola(instanteSimulado, enCola, enProceso));
        while (historialCola.size() > 100) {
            historialCola.pollFirst();
        }
    }

    /**
     * Genera una instantánea consistente de métricas acumuladas.
     */
    public EstadisticasSnapshot snapshot(long ahoraSimulado, int enColaActual, int enProcesoActual) {
        long creados = pedidosCreados.sum();
        long completados = pedidosCompletados.sum();
        long reencolados = pedidosReencolados.sum();

        double esperaPromedioGlobal = completados > 0
                ? (double) sumaEsperaGlobalSegundos.sum() / completados : 0.0;
        double procesamientoPromedioGlobal = completados > 0
                ? (double) sumaProcesamientoGlobalSegundos.sum() / completados : 0.0;
        double porcentajeSLAGlobal = completados > 0
                ? ((double) cumplieronSLAGlobal.sum() / completados) * 100.0 : 100.0;

        // Throughput en pedidos por minuto simulado
        long primerInstante = primerInstanteSimulado.get();
        long deltaSimSegundos = (primerInstante >= 0 && ahoraSimulado > primerInstante)
                ? (ahoraSimulado - primerInstante) : 1;
        double minutosSimulados = Math.max(0.1, deltaSimSegundos / 60.0);
        double throughput = completados / minutosSimulados;

        List<EstadisticasSnapshot.EstadisticaNivelSnapshot> metricasNiveles = new ArrayList<>(5);
        for (NivelServicio ns : NivelServicio.values()) {
            int i = ns.getNivel();
            long compNivel = completadosPorNivel[i].sum();
            double espProm = compNivel > 0 ? (double) sumaEsperaPorNivel[i].sum() / compNivel : 0.0;
            double procProm = compNivel > 0 ? (double) sumaProcesamientoPorNivel[i].sum() / compNivel : 0.0;
            long cSLA = cumplieronSLAPorNivel[i].sum();
            long fSLA = fallaronSLAPorNivel[i].sum();
            double pctSLA = compNivel > 0 ? ((double) cSLA / compNivel) * 100.0 : 100.0;

            metricasNiveles.add(new EstadisticasSnapshot.EstadisticaNivelSnapshot(
                    i,
                    ns.getNombre(),
                    compNivel,
                    espProm,
                    maxEsperaPorNivel[i].get(),
                    procProm,
                    cSLA,
                    fSLA,
                    pctSLA
            ));
        }

        return new EstadisticasSnapshot(
                creados,
                completados,
                reencolados,
                enColaActual,
                enProcesoActual,
                esperaPromedioGlobal,
                procesamientoPromedioGlobal,
                porcentajeSLAGlobal,
                throughput,
                Collections.unmodifiableList(metricasNiveles),
                List.copyOf(historialCola)
        );
    }

    /**
     * Reinicia todos los acumuladores (utilizado en el comparador de políticas).
     */
    public void reiniciar() {
        pedidosCreados.reset();
        pedidosCompletados.reset();
        pedidosReencolados.reset();
        sumaEsperaGlobalSegundos.reset();
        sumaProcesamientoGlobalSegundos.reset();
        cumplieronSLAGlobal.reset();
        primerInstanteSimulado.set(-1);
        historialCola.clear();

        for (int i = 1; i <= 5; i++) {
            completadosPorNivel[i].reset();
            sumaEsperaPorNivel[i].reset();
            maxEsperaPorNivel[i].set(0);
            sumaProcesamientoPorNivel[i].reset();
            cumplieronSLAPorNivel[i].reset();
            fallaronSLAPorNivel[i].reset();
        }
    }
}
