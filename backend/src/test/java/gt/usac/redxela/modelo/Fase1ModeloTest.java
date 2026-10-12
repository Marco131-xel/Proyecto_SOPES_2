package gt.usac.redxela.modelo;

import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.config.SimClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de aceptación para la Fase 1:
 * Modelo de dominio, catálogo, clientes, SLAs y reloj simulado.
 */
class Fase1ModeloTest {

    @BeforeEach
    void setUp() {
        Pedido.reiniciarSecuencialParaPruebas();
    }

    @Test
    @DisplayName("Niveles de servicio deben coincidir con la especificación de tiempos SLA")
    void testNivelesDeServicioSLA() {
        assertEquals(0, NivelServicio.EXPRES.getEsperaObjetivoMinutos());
        assertEquals(10, NivelServicio.PRIORITARIO.getEsperaObjetivoMinutos());
        assertEquals(30, NivelServicio.ESTANDAR.getEsperaObjetivoMinutos());
        assertEquals(60, NivelServicio.PROGRAMADO.getEsperaObjetivoMinutos());
        assertEquals(120, NivelServicio.ECONOMICO.getEsperaObjetivoMinutos());

        assertEquals(0, NivelServicio.EXPRES.getEsperaObjetivoSegundos());
        assertEquals(600, NivelServicio.PRIORITARIO.getEsperaObjetivoSegundos());
        assertEquals(1800, NivelServicio.ESTANDAR.getEsperaObjetivoSegundos());
        assertEquals(3600, NivelServicio.PROGRAMADO.getEsperaObjetivoSegundos());
        assertEquals(7200, NivelServicio.ECONOMICO.getEsperaObjetivoSegundos());

        assertEquals(NivelServicio.EXPRES, NivelServicio.fromNivel(1));
        assertEquals(NivelServicio.ECONOMICO, NivelServicio.fromNivel(5));
    }

    @Test
    @DisplayName("Tipos de mercancía deben tener combinaciones de recursos distintas y contención deliberada (R7, R8)")
    void testTiposMercanciaYContencion() {
        assertEquals(6, TipoMercancia.values().length, "Deben existir 6 tipos de mercancía");

        // Verificar combinaciones únicas
        long combinacionesUnicas = java.util.Arrays.stream(TipoMercancia.values())
                .map(TipoMercancia::getRecursosRequeridos)
                .distinct()
                .count();
        assertEquals(6, combinacionesUnicas, "Cada tipo de mercancía debe tener una combinación distinta de recursos (R7)");

        // Contención R8: FRÁGIL y PERECEDERO comparten ENCARGADO + CONTROL_CALIDAD
        Set<TipoRecurso> fragil = TipoMercancia.FRAGIL.getRecursosRequeridos();
        Set<TipoRecurso> perecedero = TipoMercancia.PERECEDERO.getRecursosRequeridos();
        assertTrue(fragil.contains(TipoRecurso.ENCARGADO) && perecedero.contains(TipoRecurso.ENCARGADO));
        assertTrue(fragil.contains(TipoRecurso.CONTROL_CALIDAD) && perecedero.contains(TipoRecurso.CONTROL_CALIDAD));

        // Contención Deadlock: PESADO y VOLUMINOSO comparten MONTACARGAS + CARGA
        Set<TipoRecurso> pesado = TipoMercancia.PESADO.getRecursosRequeridos();
        Set<TipoRecurso> voluminoso = TipoMercancia.VOLUMINOSO.getRecursosRequeridos();
        assertTrue(pesado.contains(TipoRecurso.MONTACARGAS) && voluminoso.contains(TipoRecurso.MONTACARGAS));
        assertTrue(pesado.contains(TipoRecurso.CARGA) && voluminoso.contains(TipoRecurso.CARGA));

        // Exclusión mutua: Escáner requerido por ELECTRÓNICO
        assertTrue(TipoMercancia.ELECTRONICO.getRecursosRequeridos().contains(TipoRecurso.ESCANER));
    }

    @Test
    @DisplayName("Catálogo de productos debe contener al menos 18 productos con representación de todos los tipos (R11)")
    void testCatalogoProductos() {
        List<Producto> productos = CatalogoProductos.obtenerTodos();
        assertTrue(productos.size() >= 18, "El catálogo debe contener al menos 18 productos");

        for (TipoMercancia tipo : TipoMercancia.values()) {
            List<Producto> porTipo = CatalogoProductos.obtenerPorTipo(tipo);
            assertTrue(porTipo.size() >= 3, "Cada tipo debe tener al menos 3 productos en catálogo");
        }

        // Búsqueda por ID existente y no existente
        Producto p = CatalogoProductos.obtenerPorId("PROD-FRAG-01");
        assertNotNull(p);
        assertEquals("Jarrón de Cerámica Vidriada", p.nombre());
        assertEquals(TipoMercancia.FRAGIL, p.tipoMercancia());
        assertTrue(p.unidadesPorUbicacion() > 0);

        assertTrue(CatalogoProductos.buscarPorId("ID-INEXISTENTE").isEmpty());
    }

    @Test
    @DisplayName("Pedido calcula unidades totales, ráfaga estimada y unión de recursos requeridos")
    void testPedidoCalculosYRecursos() {
        Cliente cliente = new Cliente("CLI-TEST", "Empresa Test", "test@test.gt", NivelServicio.PRIORITARIO);
        Producto prodFragil = CatalogoProductos.obtenerPorId("PROD-FRAG-01");
        Producto prodPesado = CatalogoProductos.obtenerPorId("PROD-PES-01");

        // Pedido multilínea combinando FRÁGIL y PESADO
        List<LineaPedido> lineas = List.of(
                new LineaPedido(prodFragil, 5),
                new LineaPedido(prodPesado, 3)
        );

        long creacionSimulada = 100;
        Pedido pedido = new Pedido(cliente, null, lineas, creacionSimulada);

        assertEquals("PED-000001", pedido.getId());
        assertEquals(NivelServicio.PRIORITARIO, pedido.getNivel());
        assertEquals(8, pedido.getTotalUnidades());
        assertEquals(8 * ConfiguracionSimulacion.SEGUNDOS_SIM_POR_UNIDAD, pedido.getDuracionEstimadaSegundos());
        assertEquals(creacionSimulada + NivelServicio.PRIORITARIO.getEsperaObjetivoSegundos(), pedido.getVenceSLA());

        // La unión de recursos debe tener:
        // FRÁGIL: ENCARGADO, CONTROL_CALIDAD, EMPAQUE
        // PESADO: ENCARGADO, MONTACARGAS, CARGA
        // Unión sin duplicados: ENCARGADO, CONTROL_CALIDAD, EMPAQUE, MONTACARGAS, CARGA (5 recursos)
        Set<TipoRecurso> recursos = pedido.getRecursosRequeridos();
        assertEquals(5, recursos.size());
        assertTrue(recursos.contains(TipoRecurso.ENCARGADO));
        assertTrue(recursos.contains(TipoRecurso.CONTROL_CALIDAD));
        assertTrue(recursos.contains(TipoRecurso.EMPAQUE));
        assertTrue(recursos.contains(TipoRecurso.MONTACARGAS));
        assertTrue(recursos.contains(TipoRecurso.CARGA));
        assertFalse(recursos.contains(TipoRecurso.ESCANER));
    }

    @Test
    @DisplayName("Gestor de clientes administra cuentas y cubre todos los niveles de servicio")
    void testGestorClientes() {
        GestorClientes gestor = new GestorClientes();
        List<Cliente> clientes = gestor.obtenerTodos();
        assertTrue(clientes.size() >= 10);

        // Validar cobertura de los 5 niveles en clientes semilla
        boolean tieneExpres = clientes.stream().anyMatch(c -> c.nivelContratado() == NivelServicio.EXPRES);
        boolean tienePrioritario = clientes.stream().anyMatch(c -> c.nivelContratado() == NivelServicio.PRIORITARIO);
        boolean tieneEstandar = clientes.stream().anyMatch(c -> c.nivelContratado() == NivelServicio.ESTANDAR);
        boolean tieneProgramado = clientes.stream().anyMatch(c -> c.nivelContratado() == NivelServicio.PROGRAMADO);
        boolean tieneEconomico = clientes.stream().anyMatch(c -> c.nivelContratado() == NivelServicio.ECONOMICO);

        assertTrue(tieneExpres && tienePrioritario && tieneEstandar && tieneProgramado && tieneEconomico);

        // Registro de nuevo cliente
        Cliente nuevo = gestor.registrarCliente("Nuevo Cliente S.A.", "nuevo@cliente.gt", NivelServicio.EXPRES);
        assertNotNull(nuevo.id());
        assertTrue(nuevo.id().startsWith("CLI-"));
        assertEquals(nuevo, gestor.obtenerPorId(nuevo.id()));
    }

    @Test
    @DisplayName("SimClock debe acelerar el tiempo de forma monótona y continua")
    void testSimClockAceleracionYContinuidad() throws InterruptedException {
        // Reloj con factor 100x (100 ms simulados por cada 1 ms real)
        SimClock clock = new SimClock(100.0);

        long inicioSim = clock.ahoraSimuladoMs();
        Thread.sleep(50); // 50 ms reales -> ~5000 ms simulados
        long finSim = clock.ahoraSimuladoMs();

        long deltaSim = finSim - inicioSim;
        assertTrue(deltaSim >= 3500 && deltaSim <= 8000,
                "Delta simulado (" + deltaSim + " ms) debe reflejar factor 100x sobre 50ms reales");

        // Cambio de factor dinámico no debe producir saltos temporales hacia atrás
        long antesCambio = clock.ahoraSimuladoMs();
        clock.setFactor(200.0);
        long despuesCambio = clock.ahoraSimuladoMs();
        assertTrue(despuesCambio >= antesCambio, "El tiempo simulado debe ser monótono creciente");

        // Prueba de pausa
        clock.pausar();
        assertTrue(clock.isPausado());
        long tiempoEnPausa1 = clock.ahoraSimuladoMs();
        Thread.sleep(30);
        long tiempoEnPausa2 = clock.ahoraSimuladoMs();
        assertEquals(tiempoEnPausa1, tiempoEnPausa2, "Durante la pausa el tiempo simulado no debe avanzar");

        // Reanudación
        clock.reanudar();
        assertFalse(clock.isPausado());
        Thread.sleep(20);
        assertTrue(clock.ahoraSimuladoMs() > tiempoEnPausa2, "Al reanudar el reloj debe continuar avanzando");
    }
}
