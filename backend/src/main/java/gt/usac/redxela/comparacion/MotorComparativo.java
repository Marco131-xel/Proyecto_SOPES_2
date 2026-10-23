package gt.usac.redxela.comparacion;

import gt.usac.redxela.estadisticas.Estadisticas;
import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.ListaEspera;
import gt.usac.redxela.planificacion.PoliticaAtencion;
import gt.usac.redxela.proceso.TrabajadorPedido;
import gt.usac.redxela.recursos.GestorRecursos;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Instancia aislada, sin Spring: reutiliza políticas, cola, semáforos, concesiones,
 * trabajadores y estadísticas del sistema. Stock suficiente y prevención activa
 * mantienen el experimento enfocado en planificación y contención de recursos.
 */
final class MotorComparativo {
    private final PoliticaAtencion politica;
    private final RelojComparativo reloj = new RelojComparativo();
    private final GestorRecursos recursos = new GestorRecursos();
    private final ListaEspera cola = new ListaEspera();
    private final Estadisticas estadisticas = new Estadisticas();
    private final Map<String, Long> finales = new ConcurrentHashMap<>();
    private final List<Thread> trabajadores = new ArrayList<>();
    private final AtomicReference<Throwable> fallo = new AtomicReference<>();
    private final Map<TipoRecurso, Long> ocupacion = new EnumMap<>(TipoRecurso.class);

    MotorComparativo(PoliticaAtencion politica) { this.politica = politica; }

    /** Llegadas simultáneas se agregan juntas; liberaciones preceden al nuevo despacho. */
    ResultadoComparacion.ResultadoPolitica ejecutar(List<SimuladorComparativo.PedidoCarga> carga) throws InterruptedException {
        List<Pedido> pedidos = carga.stream().map(SimuladorComparativo.PedidoCarga::instanciar).toList();
        int siguiente = 0;
        try {
            while (siguiente < pedidos.size() || cola.size() > 0 || !finales.isEmpty()) {
                if (Thread.interrupted()) throw new InterruptedException("Comparación interrumpida");
                if (fallo.get() != null) throw new IllegalStateException("Falló un trabajador del comparador", fallo.get());
                long ahora = reloj.ahoraSimuladoSegundos();
                while (siguiente < pedidos.size() && pedidos.get(siguiente).getInstanteCreacion() <= ahora) {
                    Pedido p = pedidos.get(siguiente++);
                    estadisticas.registrarPedidoCreado(p);
                    cola.agregar(p);
                }
                for (Pedido p : cola.obtenerCandidatosOrdenados(politica, ahora)) {
                    var concesion = recursos.intentarAdquirirTodoONada(p, ahora);
                    if (concesion.isEmpty()) { p.marcarEnEsperaRecursos(); continue; }
                    cola.remover(p);
                    finales.put(p.getId(), ahora + p.getDuracionEstimadaSegundos());
                    for (TipoRecurso tipo : p.getRecursosRequeridos()) ocupacion.merge(tipo, p.getDuracionEstimadaSegundos(), Long::sum);
                    var trabajador = new TrabajadorPedido(p, recursos, reloj, concesion.get(), () -> {
                        try {
                            if (p.getEstado() != EstadoPedido.COMPLETADO) throw new IllegalStateException("Pedido no completado: " + p.getId());
                            estadisticas.registrarPedidoCompletado(p, reloj.ahoraSimuladoSegundos());
                        } catch (Throwable e) { fallo.compareAndSet(null, e); }
                        finally { finales.remove(p.getId()); reloj.finalizarHilo(); }
                    });
                    Thread hilo = new Thread(trabajador, "comparador-" + politica.nombre() + "-" + p.getId());
                    hilo.setDaemon(true);
                    trabajadores.add(hilo);
                    reloj.registrar(hilo);
                    hilo.start();
                }
                reloj.esperarReposo();
                long proximaLlegada = siguiente < pedidos.size() ? pedidos.get(siguiente).getInstanteCreacion() : Long.MAX_VALUE;
                long proximoFin = finales.values().stream().mapToLong(Long::longValue).min().orElse(Long.MAX_VALUE);
                long proximo = Math.min(proximaLlegada, proximoFin);
                if (proximo != Long.MAX_VALUE) reloj.avanzarHasta(proximo);
                else if (cola.size() > 0) throw new IllegalStateException("Cola sin progreso en el comparador");
            }
            if (fallo.get() != null) throw new IllegalStateException("Falló un trabajador del comparador", fallo.get());
            if (recursos.snapshot().values().stream().anyMatch(p -> p.disponibles() != p.capacidadTotal()))
                throw new IllegalStateException("Quedaron recursos retenidos al terminar la comparación");
            return construirResultado(pedidos);
        } finally {
            // También libera concesiones al cerrar Spring o cancelar el hilo conductor.
            trabajadores.stream().filter(Thread::isAlive).forEach(Thread::interrupt);
            for (Thread hilo : trabajadores) {
                if (hilo.isAlive()) hilo.join(2000);
            }
        }
    }

    /** Integra unidades ocupadas × segundos sobre el horizonte total de la corrida. */
    private ResultadoComparacion.ResultadoPolitica construirResultado(List<Pedido> pedidos) {
        long horizonte = reloj.ahoraSimuladoSegundos() - pedidos.getFirst().getInstanteCreacion();
        var resumen = estadisticas.snapshot(reloj.ahoraSimuladoSegundos(), 0, 0);
        Map<TipoRecurso, Double> porcentajes = new EnumMap<>(TipoRecurso.class);
        long segundosUnidad = 0;
        int capacidadTotal = 0;
        for (TipoRecurso tipo : TipoRecurso.values()) {
            long ocupado = ocupacion.getOrDefault(tipo, 0L);
            porcentajes.put(tipo, 100.0 * ocupado / (tipo.getCapacidadDefecto() * (double) horizonte));
            segundosUnidad += ocupado;
            capacidadTotal += tipo.getCapacidadDefecto();
        }
        long inanicion = pedidos.stream().filter(p -> p.calcularEsperaSegundos(0) > 2 * p.getNivel().getEsperaObjetivoSegundos()).count();
        long maxima = pedidos.stream().mapToLong(p -> p.calcularEsperaSegundos(0)).max().orElse(0);
        return new ResultadoComparacion.ResultadoPolitica(politica.nombre(), politica.descripcion(), pedidos.size(), horizonte,
                resumen.esperaPromedioGlobalSegundos(), maxima, resumen.porcentajeCumplimientoSLAGlobal(),
                pedidos.size() * 60.0 / horizonte, 100.0 * segundosUnidad / (capacidadTotal * (double) horizonte),
                inanicion, resumen.metricasPorNivel(), porcentajes);
    }
}
