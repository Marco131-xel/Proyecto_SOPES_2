package gt.usac.redxela.comparacion;

import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Casos calculables a mano, repetibilidad completa y control de trabajos concurrentes. */
@Timeout(30)
class Fase7ComparadorTest {
    private SimuladorComparativo.PedidoCarga pedido(String id, int nivel, int unidades, String producto) {
        return new SimuladorComparativo.PedidoCarga(id, NivelServicio.fromNivel(nivel),
                List.of(new LineaPedido(CatalogoProductos.obtenerPorId(producto), unidades)), 0);
    }

    @Test void politicasProducenLosTiemposEsperadosConUnEscaner() throws Exception {
        var carga = List.of(pedido("A", 1, 10, "PROD-ELEC-01"), pedido("B", 5, 1, "PROD-ELEC-01"), pedido("C", 2, 4, "PROD-ELEC-01"));
        var estricta = new MotorComparativo(new PoliticaPrioridadEstricta()).ejecutar(carga);
        var sjf = new MotorComparativo(new PoliticaMenorCantidadPrimero()).ejecutar(carga);
        assertEquals(450, estricta.horizonteSimuladoSegundos());
        assertEquals(240, estricta.esperaPromedioSegundos());
        assertEquals(420, estricta.esperaMaximaSegundos());
        assertEquals(60, sjf.esperaPromedioSegundos());
        assertEquals(100, estricta.porcentajeSLA());
        assertEquals(200.0 / 3, sjf.porcentajeSLA(), 1e-9);
        assertEquals(100, sjf.ocupacionPorRecurso().get(TipoRecurso.ESCANER));
        assertEquals(300.0 / 26, sjf.ocupacionMediaPorcentaje(), 1e-9);
        assertEquals(1, sjf.pedidosConInanicion()); // Exprés: objetivo cero, espera positiva.
    }

    @Test void dosTrabajadoresPuedenRetenerRecursosYProcesarEnParalelo() throws Exception {
        var carga = List.of(pedido("A", 3, 1, "PROD-GEN-01"), pedido("B", 3, 1, "PROD-GEN-01"), pedido("C", 3, 1, "PROD-GEN-01"));
        var resultado = new MotorComparativo(new PoliticaPrioridadEstricta()).ejecutar(carga);
        assertEquals(60, resultado.horizonteSimuladoSegundos());
        assertEquals(10, resultado.esperaPromedioSegundos());
        assertEquals(75, resultado.ocupacionPorRecurso().get(TipoRecurso.CARGA));
        assertEquals(3, resultado.throughputPedidosPorMinuto());
    }

    @Test void semillaRepiteTodosLosResultadosSinConsumirIdsGlobales() throws Exception {
        var simulador = new SimuladorComparativo();
        var config = new ConfiguracionComparacion(42, 80, 4);
        long antes = Long.parseLong(Pedido.generarSiguienteId().substring(4));
        var uno = simulador.ejecutar(config);
        var dos = simulador.ejecutar(config);
        assertEquals(uno, dos);
        assertEquals(antes + 1, Long.parseLong(Pedido.generarSiguienteId().substring(4)));
        assertNotEquals(uno.huellaCarga(), simulador.ejecutar(new ConfiguracionComparacion(43, 80, 4)).huellaCarga());
        assertEquals(4, uno.politicas().size());
        assertFalse(Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.isAlive() && t.getName().startsWith("comparador-")));
        for (var p : uno.politicas()) {
            assertEquals(80, p.pedidosCompletados());
            assertEquals(80, p.niveles().stream().mapToLong(n -> n.completados()).sum());
            assertTrue(p.ocupacionPorRecurso().values().stream().allMatch(v -> v >= 0 && v <= 100));
        }
        assertThrows(UnsupportedOperationException.class, () -> uno.politicas().clear());
    }

    @Test void cargaPorDefectoRepetibleIncluyeCincoNivelesYLimites() throws Exception {
        var simulador = new SimuladorComparativo();
        var carga = simulador.generarCarga(ConfiguracionComparacion.porDefecto());
        assertEquals(300, carga.size());
        assertEquals(carga, simulador.generarCarga(ConfiguracionComparacion.porDefecto()));
        assertEquals(5, carga.stream().map(SimuladorComparativo.PedidoCarga::nivel).distinct().count());
        assertEquals(simulador.ejecutar(ConfiguracionComparacion.porDefecto()), simulador.ejecutar(ConfiguracionComparacion.porDefecto()));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionComparacion(1, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionComparacion(1, 1001, 4));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionComparacion(1, 5, Double.NaN));
    }

    @Test void servicioRechazaSolapamientoYPublicaElResultado() throws Exception {
        CountDownLatch iniciado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        var simulador = new SimuladorComparativo() {
            @Override public ResultadoComparacion ejecutar(ConfiguracionComparacion config) throws InterruptedException {
                iniciado.countDown(); liberar.await(); return super.ejecutar(config);
            }
        };
        try (var servicio = new ServicioComparacion(simulador)) {
            assertEquals("EJECUTANDO", servicio.iniciar(new ConfiguracionComparacion(42, 1, 4)).estado());
            assertTrue(iniciado.await(2, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> servicio.iniciar(ConfiguracionComparacion.porDefecto()));
            liberar.countDown();
            while (servicio.obtenerEstado().estado().equals("EJECUTANDO")) Thread.sleep(10);
            assertEquals("COMPLETADA", servicio.obtenerEstado().estado());
            assertNotNull(servicio.obtenerEstado().resultado());
        } finally { liberar.countDown(); }
    }

    @Test void cierreInterrumpeElTrabajoYPublicaError() throws Exception {
        CountDownLatch iniciado = new CountDownLatch(1);
        var servicio = new ServicioComparacion(new SimuladorComparativo() {
            @Override public ResultadoComparacion ejecutar(ConfiguracionComparacion config) throws InterruptedException {
                iniciado.countDown(); new CountDownLatch(1).await(); throw new AssertionError("Debe interrumpirse");
            }
        });
        try {
            servicio.iniciar(ConfiguracionComparacion.porDefecto());
            assertTrue(iniciado.await(2, TimeUnit.SECONDS));
            servicio.close();
            while (servicio.obtenerEstado().estado().equals("EJECUTANDO")) Thread.sleep(10);
            assertEquals("ERROR", servicio.obtenerEstado().estado());
            assertThrows(IllegalStateException.class, () -> servicio.iniciar(ConfiguracionComparacion.porDefecto()));
        } finally { servicio.close(); }
    }
}
