package gt.usac.redxela.planificacion;

import gt.usac.redxela.estadisticas.Estadisticas;
import gt.usac.redxela.estadisticas.EstadisticasSnapshot;
import gt.usac.redxela.modelo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de aceptación para la Fase 3:
 * Políticas de planificación (Prioridad Estricta, Aging, SJF, EDF),
 * prevención de inanición, cambio en caliente y módulo de Estadísticas thread-safe.
 */
class Fase3PoliticasYEstadisticasTest {

    private Cliente cliente;
    private Producto prodGeneral;

    @BeforeEach
    void setUp() {
        Pedido.reiniciarSecuencialParaPruebas();
        cliente = new Cliente("CLI-TEST", "Cliente Prueba", "test@test.gt", NivelServicio.ESTANDAR);
        prodGeneral = CatalogoProductos.obtenerPorId("PROD-GEN-01");
    }

    @Test
    @DisplayName("Prioridad Estricta: Ordena estrictamente por nivel de servicio y desempata por FIFO")
    void testPoliticaPrioridadEstricta() {
        PoliticaPrioridadEstricta politica = new PoliticaPrioridadEstricta();

        Pedido pNivel5 = new Pedido(cliente, NivelServicio.ECONOMICO, List.of(new LineaPedido(prodGeneral, 1)), 10);
        Pedido pNivel2Viejo = new Pedido(cliente, NivelServicio.PRIORITARIO, List.of(new LineaPedido(prodGeneral, 1)), 20);
        Pedido pNivel2Nuevo = new Pedido(cliente, NivelServicio.PRIORITARIO, List.of(new LineaPedido(prodGeneral, 1)), 30);
        Pedido pNivel1 = new Pedido(cliente, NivelServicio.EXPRES, List.of(new LineaPedido(prodGeneral, 1)), 40);

        List<Pedido> lista = new ArrayList<>(List.of(pNivel5, pNivel2Viejo, pNivel2Nuevo, pNivel1));
        lista.sort(politica.comparador(100));

        // Orden esperado: Nivel 1 -> Nivel 2 (viejo) -> Nivel 2 (nuevo) -> Nivel 5
        assertEquals(pNivel1, lista.get(0), "Exprés debe ir de primero");
        assertEquals(pNivel2Viejo, lista.get(1), "Nivel 2 con llegada anterior desempata antes");
        assertEquals(pNivel2Nuevo, lista.get(2), "Nivel 2 más reciente va después");
        assertEquals(pNivel5, lista.get(3), "Nivel 5 queda de último");
    }

    @Test
    @DisplayName("Aging: Pedido Nivel 5 envejecido gana prioridad sobre Nivel 2 nuevo, evitando inanición")
    void testPoliticaPrioridadConAgingEvitaInanicion() {
        long tAging = 600; // 10 minutos simulados por promoción
        PoliticaPrioridadConAging aging = new PoliticaPrioridadConAging(tAging);

        // Pedido Económico (Nivel 5) creado en t = 0
        Pedido pEconomicoViejo = new Pedido(cliente, NivelServicio.ECONOMICO, List.of(new LineaPedido(prodGeneral, 1)), 0);
        // Pedido Prioritario (Nivel 2) recién llegado en t = 2500
        Pedido pPrioritarioNuevo = new Pedido(cliente, NivelServicio.PRIORITARIO, List.of(new LineaPedido(prodGeneral, 1)), 2500);

        // En t = 100: el económico ha esperado 100s (< 600s), su nivel efectivo sigue siendo 5
        assertEquals(5, aging.calcularNivelEfectivo(pEconomicoViejo, 100));
        assertTrue(aging.comparador(100).compare(pPrioritarioNuevo, pEconomicoViejo) < 0,
                "Sin envejecer, Nivel 2 tiene prioridad sobre Nivel 5");

        // En t = 2500: el económico ha esperado 2500s. Promociones = floor(2500 / 600) = 4 promociones!
        // Nivel efectivo = max(1, 5 - 4) = 1 (¡Ascendió a Nivel 1 Exprés!)
        assertEquals(1, aging.calcularNivelEfectivo(pEconomicoViejo, 2500));
        assertEquals(2, aging.calcularNivelEfectivo(pPrioritarioNuevo, 2500));

        // Al evaluar el comparador en t = 2500, el Económico viejo pasa al frente del Prioritario nuevo
        int comparacion = aging.comparador(2500).compare(pEconomicoViejo, pPrioritarioNuevo);
        assertTrue(comparacion < 0, "El pedido envejecido debe ganar prioridad, evitando la inanición (Starvation-free)");
    }

    @Test
    @DisplayName("SJF (Menor Cantidad Primero): Prioriza pedidos pequeños sobre pedidos masivos")
    void testPoliticaMenorCantidadPrimeroSJF() {
        PoliticaMenorCantidadPrimero sjf = new PoliticaMenorCantidadPrimero();

        // Pedido pequeño: 2 unidades, Nivel 4 (Programado)
        Pedido pPequeno = new Pedido(cliente, NivelServicio.PROGRAMADO, List.of(new LineaPedido(prodGeneral, 2)), 100);
        // Pedido masivo: 50 unidades, Nivel 1 (Exprés)
        Pedido pMasivo = new Pedido(cliente, NivelServicio.EXPRES, List.of(new LineaPedido(prodGeneral, 50)), 50);

        List<Pedido> lista = new ArrayList<>(List.of(pMasivo, pPequeno));
        lista.sort(sjf.comparador(200));

        assertEquals(pPequeno, lista.get(0), "SJF debe anteponer el pedido de menor tamaño (2 unidades vs 50)");
        assertEquals(pMasivo, lista.get(1));
    }

    @Test
    @DisplayName("EDF: Prioriza pedidos cuya fecha límite (Deadline / SLA) está más próxima a expirar")
    void testPoliticaEDFEarliestDeadlineFirst() {
        PoliticaEDF edf = new PoliticaEDF();

        // Pedido A: Nivel 1 (Exprés: SLA = 0s), creado en t = 500 -> Vence en t = 500
        Pedido pExpres = new Pedido(cliente, NivelServicio.EXPRES, List.of(new LineaPedido(prodGeneral, 1)), 500);
        // Pedido B: Nivel 5 (Económico: SLA = 7200s), creado en t = 100 -> Vence en t = 7300
        Pedido pEconomico = new Pedido(cliente, NivelServicio.ECONOMICO, List.of(new LineaPedido(prodGeneral, 1)), 100);

        // A pesar de que el económico llegó antes (t=100 vs t=500), el SLA de Exprés vence mucho antes
        List<Pedido> lista = new ArrayList<>(List.of(pEconomico, pExpres));
        lista.sort(edf.comparador(600));

        assertEquals(pExpres, lista.get(0), "EDF debe colocar primero el pedido cuyo SLA vence antes");
        assertEquals(pEconomico, lista.get(1));
    }

    @Test
    @DisplayName("GestorPoliticas: Registra las 4 políticas y permite cambio dinámico en caliente (R5)")
    void testGestorPoliticasYCambioEnCaliente() {
        PoliticaPrioridadEstricta estricta = new PoliticaPrioridadEstricta();
        PoliticaPrioridadConAging aging = new PoliticaPrioridadConAging();
        PoliticaMenorCantidadPrimero sjf = new PoliticaMenorCantidadPrimero();
        PoliticaEDF edf = new PoliticaEDF();

        GestorPoliticas gestor = new GestorPoliticas(estricta, aging, sjf, edf);

        assertEquals(4, gestor.obtenerTodas().size(), "Deben estar registradas las 4 políticas");
        assertEquals(estricta, gestor.obtenerPorNombre("PRIORIDAD_ESTRICTA"));
        assertEquals(aging, gestor.obtenerPorNombre("PRIORIDAD_CON_AGING"));
        assertEquals(sjf, gestor.obtenerPorNombre("MENOR_CANTIDAD_PRIMERO"));
        assertEquals(edf, gestor.obtenerPorNombre("EDF"));

        assertThrows(IllegalArgumentException.class, () -> gestor.obtenerPorNombre("POLITICA_INEXISTENTE"));
    }

    @Test
    @DisplayName("Estadisticas: Agregación thread-safe de métricas de SLA y tiempos sin condiciones de carrera")
    void testEstadisticasThreadSafeYCumplimientoSLA() throws InterruptedException {
        Estadisticas stats = new Estadisticas();

        int numHilos = 10;
        int pedidosPorHilo = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        CountDownLatch latch = new CountDownLatch(numHilos);

        for (int i = 0; i < numHilos; i++) {
            final int hiloIdx = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < pedidosPorHilo; j++) {
                        NivelServicio nivel = NivelServicio.fromNivel((hiloIdx % 5) + 1);
                        Pedido p = new Pedido(cliente, nivel, List.of(new LineaPedido(prodGeneral, 1)), 0);
                        stats.registrarPedidoCreado(p);

                        // Simular espera y procesamiento
                        long espera = (j % 2 == 0) ? nivel.getEsperaObjetivoSegundos() : nivel.getEsperaObjetivoSegundos() + 100;
                        p.iniciarProcesamiento(espera);
                        long fin = espera + 30; // 30s de trabajo
                        p.completarProcesamiento(fin);

                        stats.registrarPedidoCompletado(p, fin);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        EstadisticasSnapshot snapshot = stats.snapshot(1000, 0, 0);

        assertEquals(200, snapshot.pedidosCreados());
        assertEquals(200, snapshot.pedidosCompletados());
        assertTrue(snapshot.esperaPromedioGlobalSegundos() > 0);
        assertTrue(snapshot.procesamientoPromedioGlobalSegundos() > 0);
        assertEquals(5, snapshot.metricasPorNivel().size());

        // Comprobar que exactamente el 50% cumplió SLA según la lógica de prueba (j % 2 == 0)
        assertEquals(50.0, snapshot.porcentajeCumplimientoSLAGlobal(), 1.0);
    }
}
