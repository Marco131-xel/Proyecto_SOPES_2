package gt.usac.redxela.calidad;

import gt.usac.redxela.almacen.Almacen;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.deadlock.*;
import gt.usac.redxela.estadisticas.Estadisticas;
import gt.usac.redxela.generador.GeneradorPedidos;
import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.*;
import gt.usac.redxela.recepcion.Recepcion;
import gt.usac.redxela.recursos.GestorRecursos;
import org.junit.jupiter.api.*;
import java.util.HashSet;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Regresiones de dominio, publicación inmutable y recuperación sin reconstruir el ciclo. */
@Timeout(20)
class Fase8CalidadTest {
    private void esperar(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + 8_000_000_000L;
        while (!condicion.getAsBoolean() && System.nanoTime() < limite) Thread.sleep(10);
        assertTrue(condicion.getAsBoolean(), "No se alcanzó la condición dentro del plazo");
    }

    @Test void generadorRespetaElContratoYRepiteLosProductosConSemilla() {
        var cola = new ListaEspera();
        var recursos = new GestorRecursos();
        var reloj = new SimClock();
        var despachador = new Despachador(cola, recursos, reloj, new PoliticaPrioridadEstricta());
        var generador = new GeneradorPedidos(new GestorClientes(), cola, despachador, reloj);
        generador.setSemilla(42L);
        var primero = generador.generarPedidoAleatorio();
        generador.setSemilla(42L);
        var repetido = generador.generarPedidoAleatorio();
        assertEquals(primero.getLineas(), repetido.getLineas());
        assertEquals(primero.getCliente(), repetido.getCliente());
        var niveles = new HashSet<NivelServicio>();
        for (int i = 0; i < 500; i++) {
            var p = generador.generarPedidoAleatorio();
            assertEquals(p.getCliente().nivelContratado(), p.getNivel());
            assertTrue(p.getLineas().size() >= 1 && p.getLineas().size() <= 4);
            assertEquals(p.getLineas().size(), p.getLineas().stream().map(l -> l.producto().id()).distinct().count());
            assertTrue(p.getLineas().stream().allMatch(l -> l.cantidad() >= 1 && l.cantidad() <= 40));
            niveles.add(p.getNivel());
        }
        assertEquals(5, niveles.size());
        assertThrows(IllegalArgumentException.class, () -> generador.setTasaPedidosPorMinutoSimulado(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new SimClock(Double.POSITIVE_INFINITY));
    }

    @Test void fotografiasNoPermitenModificarLasColeccionesPublicadas() {
        var almacen = new Almacen();
        assertThrows(UnsupportedOperationException.class, () -> almacen.snapshot(0).ubicaciones().clear());
        var recursos = new GestorRecursos();
        var pool = recursos.snapshot().get(TipoRecurso.CARGA);
        assertThrows(UnsupportedOperationException.class, () -> pool.unidades().clear());
        var recepcion = new Recepcion(recursos, new SimClock());
        recepcion.registrarLlegada(CatalogoProductos.obtenerPorId("PROD-GEN-01"), 1);
        assertThrows(UnsupportedOperationException.class, () -> recepcion.snapshot().camionesEnPatioDetalle().clear());
    }

    @Test void pausaDelRelojNoAdmiteLlegadasAutomaticas() throws InterruptedException {
        var cola = new ListaEspera();
        var reloj = new SimClock(600);
        reloj.pausar();
        var despachador = new Despachador(cola, new GestorRecursos(), reloj, new PoliticaPrioridadEstricta());
        var generador = new GeneradorPedidos(new GestorClientes(), cola, despachador, reloj);
        try {
            generador.iniciar();
            Thread.sleep(150);
            assertEquals(0, cola.size());
            reloj.reanudar();
            esperar(() -> cola.size() > 0);
            generador.detener();
            int detenidos = cola.size();
            Thread.sleep(100);
            assertEquals(detenidos, cola.size());
        } finally { generador.detener(); }
    }

    @RepeatedTest(2)
    void escenarioCoordinadoPermiteElegirCualquieraDeLosGanadores(RepetitionInfo repeticion) throws Exception {
        var cola = new ListaEspera();
        var reloj = new SimClock(600);
        var recursos = new GestorRecursos();
        var despachador = new Despachador(cola, recursos, reloj, new PoliticaPrioridadEstricta(), new Estadisticas());
        var detector = new DetectorDeadlock(despachador, recursos, reloj);
        var escenario = new GestorEscenarioDeadlock(recursos, despachador, detector, cola, new GestorClientes(), reloj);
        try {
            despachador.iniciar();
            var pedidos = escenario.provocarEscenarioDeadlock();
            esperar(() -> detector.ejecutarDeteccion().hayConflicto());
            assertEquals(2, detector.getUltimoInforme().pedidosInvolucrados().size());
            int ganador = repeticion.getCurrentRepetition() - 1;
            escenario.resolverConflictoManual(pedidos.get(ganador).getId());
            assertTrue(recursos.isPrevencionDeadlock());
            esperar(() -> despachador.getPedidosCompletados().size() == 2);
            assertTrue(pedidos.get(1 - ganador).getVecesReencolado() > 0);
        } finally {
            despachador.detener();
            escenario.restaurarMantenimientoYPrevencion();
        }
        assertTrue(recursos.snapshot().values().stream().allMatch(p -> p.disponibles() == p.capacidadTotal()));
    }
}
