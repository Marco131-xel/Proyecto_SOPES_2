package gt.usac.redxela.deadlock;

import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.estadisticas.Estadisticas;
import gt.usac.redxela.modelo.EstadoPedido;
import gt.usac.redxela.modelo.GestorClientes;
import gt.usac.redxela.modelo.Pedido;
import gt.usac.redxela.modelo.TipoRecurso;
import gt.usac.redxela.planificacion.Despachador;
import gt.usac.redxela.planificacion.ListaEspera;
import gt.usac.redxela.planificacion.PoliticaPrioridadEstricta;
import gt.usac.redxela.recursos.GestorRecursos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias y de integración para la Fase 5:
 * Detección formal de interbloqueos mediante matrices, provocación controlada (R14)
 * y resolución manual con liberación y retroceso de víctimas (R15).
 */
class Fase5DeadlockTest {

    @BeforeEach
    void setUp() {
        Pedido.reiniciarSecuencialParaPruebas();
    }

    @Test
    @DisplayName("Detección: Sistema en operación normal no reporta interbloqueos")
    void testDeteccionMatricesSinDeadlock() {
        SimClock clock = new SimClock(100.0);
        GestorRecursos gestorRecursos = new GestorRecursos();
        ListaEspera listaEspera = new ListaEspera();
        PoliticaPrioridadEstricta politica = new PoliticaPrioridadEstricta();
        Estadisticas stats = new Estadisticas();

        Despachador despachador = new Despachador(listaEspera, gestorRecursos, clock, politica, stats);
        DetectorDeadlock detector = new DetectorDeadlock(despachador, gestorRecursos, clock);

        InformeConflicto informe = detector.ejecutarDeteccion();
        assertFalse(informe.hayConflicto());
        assertTrue(informe.pedidosInvolucrados().isEmpty());
        assertTrue(informe.grafoAsignacion().isEmpty());
    }

    @Test
    @DisplayName("Escenario provocado: Detección formal y resolución manual con finalización exitosa (R14, R15)")
    void testEscenarioProvocadoDeadlockRealYResolucionManual() throws InterruptedException {
        // Reloj acelerado
        SimClock clock = new SimClock(200.0);
        GestorRecursos gestorRecursos = new GestorRecursos();
        ListaEspera listaEspera = new ListaEspera();
        PoliticaPrioridadEstricta politica = new PoliticaPrioridadEstricta();
        Estadisticas stats = new Estadisticas();
        GestorClientes gestorClientes = new GestorClientes();

        Despachador despachador = new Despachador(listaEspera, gestorRecursos, clock, politica, stats);
        DetectorDeadlock detector = new DetectorDeadlock(despachador, gestorRecursos, clock);
        GestorEscenarioDeadlock gestorEscenario = new GestorEscenarioDeadlock(
                gestorRecursos, despachador, detector, listaEspera, gestorClientes, clock);

        despachador.iniciar();

        // 1. Provocar el interbloqueo con órdenes cruzados [MTC, CRG] vs [CRG, MTC]
        List<Pedido> pedidos = gestorEscenario.provocarEscenarioDeadlock();
        Pedido pedidoA = pedidos.get(0);
        Pedido pedidoB = pedidos.get(1);

        // Esperar a que ambos pedidos adquieran su primer recurso y se bloqueen esperando el segundo
        // Pausa configurada entre adquisiciones: 1.5s real
        long inicioEspera = System.currentTimeMillis();
        InformeConflicto informe = detector.ejecutarDeteccion();

        while (!informe.hayConflicto() && (System.currentTimeMillis() - inicioEspera < 6000)) {
            Thread.sleep(300);
            informe = detector.ejecutarDeteccion();
        }

        // 2. Verificaciones del interbloqueo detectado (R14)
        assertTrue(informe.hayConflicto(), "El detector matricial debió confirmar el interbloqueo real");
        assertEquals(2, informe.pedidosInvolucrados().size(), "Deben estar involucrados exactamente 2 pedidos");
        assertTrue(informe.pedidosInvolucrados().contains(pedidoA.getId()));
        assertTrue(informe.pedidosInvolucrados().contains(pedidoB.getId()));

        assertEquals(EstadoPedido.BLOQUEADO, pedidoA.getEstado());
        assertEquals(EstadoPedido.BLOQUEADO, pedidoB.getEstado());

        // Verificar el Grafo de Asignación de Recursos (RAG)
        assertFalse(informe.grafoAsignacion().isEmpty());
        boolean tieneRetiene = informe.grafoAsignacion().stream()
                .anyMatch(a -> a.tipoArista() == AristaAsignacion.TipoArista.RETIENE);
        boolean tieneSolicita = informe.grafoAsignacion().stream()
                .anyMatch(a -> a.tipoArista() == AristaAsignacion.TipoArista.SOLICITUD);
        assertTrue(tieneRetiene && tieneSolicita, "El RAG debe contener aristas de RETIENE y SOLICITA");

        // 3. Resolución manual (R15): Elegimos al Pedido A como ganador
        gestorEscenario.resolverConflictoManual(pedidoA.getId());

        // Esperar a que el ganador (A) finalice y el perdedor (B) se reencole y complete
        long limiteFin = System.currentTimeMillis() + 8000;
        while ((pedidoA.getEstado() != EstadoPedido.COMPLETADO || pedidoB.getEstado() != EstadoPedido.COMPLETADO)
                && System.currentTimeMillis() < limiteFin) {
            Thread.sleep(200);
        }

        despachador.detener();
        gestorEscenario.restaurarMantenimientoYPrevencion();

        // 4. Verificaciones posteriores a la resolución
        assertEquals(EstadoPedido.COMPLETADO, pedidoA.getEstado(), "El pedido ganador debió completarse");
        assertEquals(EstadoPedido.COMPLETADO, pedidoB.getEstado(), "El pedido víctima debió reencolarse y completarse");
        assertTrue(pedidoB.getVecesReencolado() >= 1, "La víctima debió incrementar su contador de reencolados");

        // Invariante de oro: Los semáforos restauraron todos sus permisos sin fugas
        assertEquals(TipoRecurso.MONTACARGAS.getCapacidadDefecto(),
                gestorRecursos.getPool(TipoRecurso.MONTACARGAS).getPermisosDisponibles());
        assertEquals(TipoRecurso.CARGA.getCapacidadDefecto(),
                gestorRecursos.getPool(TipoRecurso.CARGA).getPermisosDisponibles());
    }
}
