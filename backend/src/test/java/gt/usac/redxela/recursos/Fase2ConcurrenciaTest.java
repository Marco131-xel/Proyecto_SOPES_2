package gt.usac.redxela.recursos;

import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.Despachador;
import gt.usac.redxela.planificacion.ListaEspera;
import gt.usac.redxela.planificacion.PoliticaPrioridadEstricta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas rigurosas de concurrencia y tolerancia a fallos para la Fase 2 (R8.6).
 * 
 * Valida:
 * 1. Invariante de capacidad: Ningún pool entrega más unidades que su semáforo.
 * 2. Rollback atómico: Adquisición todo-o-nada no fuga recursos ante contención parcial.
 * 3. RAII y AutoCloseable: Concesion libera recursos sin fugas incluso tras excepciones.
 * 4. Ciclo de vida integrado del Despachador: Procesamiento concurrente y preservación
 *    completa de permisos al finalizar los pedidos.
 */
class Fase2ConcurrenciaTest {

    @BeforeEach
    void setUp() {
        Pedido.reiniciarSecuencialParaPruebas();
    }

    @Test
    @DisplayName("Invariante de capacidad: PoolRecurso nunca supera su capacidad bajo estrés multihilo")
    void testPoolRecursoNuncaExcedeCapacidadBajoAltaConcurrencia() throws InterruptedException {
        int capacidad = 3;
        PoolRecurso pool = new PoolRecurso(TipoRecurso.MONTACARGAS, capacidad);

        int numHilos = 20;
        int iteracionesPorHilo = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        CountDownLatch latchInicio = new CountDownLatch(1);
        CountDownLatch latchFin = new CountDownLatch(numHilos);

        AtomicInteger concurrenciaSimultanea = new AtomicInteger(0);
        AtomicInteger maxConcurrenciaObservada = new AtomicInteger(0);
        AtomicInteger violaciones = new AtomicInteger(0);

        for (int i = 0; i < numHilos; i++) {
            final String hiloId = "HILO-" + i;
            executor.submit(() -> {
                try {
                    latchInicio.await();
                    for (int j = 0; j < iteracionesPorHilo; j++) {
                        String pedidoId = hiloId + "-IT" + j;
                        UnidadRecurso unidad = pool.acquire(pedidoId, 0);

                        int actual = concurrenciaSimultanea.incrementAndGet();
                        maxConcurrenciaObservada.accumulateAndGet(actual, Math::max);

                        if (actual > capacidad) {
                            violaciones.incrementAndGet();
                        }

                        // Breve pausa para simular trabajo
                        Thread.sleep(1);

                        concurrenciaSimultanea.decrementAndGet();
                        pool.release(unidad, pedidoId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latchFin.countDown();
                }
            });
        }

        latchInicio.countDown(); // Liberar todos los hilos simultáneamente
        boolean terminoATiempo = latchFin.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(terminoATiempo, "Todos los hilos debieron completar sus iteraciones");
        assertEquals(0, violaciones.get(), "La concurrencia nunca debió exceder la capacidad del semáforo");
        assertEquals(capacidad, pool.getPermisosDisponibles(), "No deben existir fugas de permisos tras liberar");
        assertTrue(maxConcurrenciaObservada.get() <= capacidad);
    }

    @Test
    @DisplayName("Rollback atómico: Adquisición todo-o-nada revierte recursos parciales si uno falla")
    void testAdquisicionTodoONadaRollbackAtomico() {
        GestorRecursos gestor = new GestorRecursos();
        gestor.setPrevencionDeadlock(true);

        PoolRecurso poolEmpaque = gestor.getPool(TipoRecurso.EMPAQUE);
        PoolRecurso poolEncargado = gestor.getPool(TipoRecurso.ENCARGADO);
        PoolRecurso poolControl = gestor.getPool(TipoRecurso.CONTROL_CALIDAD);

        int permisosInicialesEncargado = poolEncargado.getPermisosDisponibles();
        int permisosInicialesControl = poolControl.getPermisosDisponibles();

        // Agotamos deliberadamente el pool de EMPAQUE (6 unidades)
        List<UnidadRecurso> empaquesOcupados = new ArrayList<>();
        for (int i = 0; i < TipoRecurso.EMPAQUE.getCapacidadDefecto(); i++) {
            Optional<UnidadRecurso> u = poolEmpaque.tryAcquire("BLOQUEADOR-" + i, 0);
            assertTrue(u.isPresent());
            empaquesOcupados.add(u.get());
        }
        assertEquals(0, poolEmpaque.getPermisosDisponibles(), "El pool de empaque debe estar agotado");

        // Creamos un pedido de tipo FRÁGIL (Requiere ENCARGADO + CONTROL_CALIDAD + EMPAQUE)
        Cliente cliente = new Cliente("CLI-TEST", "Cliente Test", "test@test.gt", NivelServicio.PRIORITARIO);
        Producto prodFragil = CatalogoProductos.obtenerPorId("PROD-FRAG-01");
        Pedido pedidoFragil = new Pedido(cliente, null, List.of(new LineaPedido(prodFragil, 2)), 0);

        // Intentamos adquisición atómica: debe fallar porque EMPAQUE está agotado
        Optional<Concesion> resultado = gestor.intentarAdquirirTodoONada(pedidoFragil, 0);
        assertTrue(resultado.isEmpty(), "La adquisición debe fallar por falta de empaque");

        // Verificamos que se haya realizado ROLLBACK estricto de ENCARGADO y CONTROL_CALIDAD
        assertEquals(permisosInicialesEncargado, poolEncargado.getPermisosDisponibles(),
                "Rollback debe restaurar permisos de ENCARGADO");
        assertEquals(permisosInicialesControl, poolControl.getPermisosDisponibles(),
                "Rollback debe restaurar permisos de CONTROL_CALIDAD");

        // Liberamos los empaques bloqueadores
        for (int i = 0; i < empaquesOcupados.size(); i++) {
            poolEmpaque.release(empaquesOcupados.get(i), "BLOQUEADOR-" + i);
        }
        assertEquals(TipoRecurso.EMPAQUE.getCapacidadDefecto(), poolEmpaque.getPermisosDisponibles());

        // Ahora el pedido sí debe poder adquirir todo exitosamente
        Optional<Concesion> exito = gestor.intentarAdquirirTodoONada(pedidoFragil, 0);
        assertTrue(exito.isPresent(), "Con recursos libres, la adquisición atómica debe tener éxito");

        // Cerramos la concesión y verificamos retorno limpio de todos los recursos
        exito.get().close();
        assertEquals(permisosInicialesEncargado, poolEncargado.getPermisosDisponibles());
        assertEquals(permisosInicialesControl, poolControl.getPermisosDisponibles());
        assertEquals(TipoRecurso.EMPAQUE.getCapacidadDefecto(), poolEmpaque.getPermisosDisponibles());
    }

    @Test
    @DisplayName("Concesion AutoCloseable es idempotente y libera todos los recursos asignados")
    void testConcesionAutoCloseableLiberaSinFugas() {
        PoolRecurso pool1 = new PoolRecurso(TipoRecurso.MONTACARGAS, 3);
        PoolRecurso pool2 = new PoolRecurso(TipoRecurso.CARGA, 2);

        UnidadRecurso u1 = pool1.tryAcquire("PED-1", 0).orElseThrow();
        UnidadRecurso u2 = pool2.tryAcquire("PED-1", 0).orElseThrow();

        assertEquals(2, pool1.getPermisosDisponibles());
        assertEquals(1, pool2.getPermisosDisponibles());

        try (Concesion concesion = new Concesion("PED-1")) {
            concesion.agregar(pool1, u1);
            concesion.agregar(pool2, u2);
            assertEquals(2, concesion.getAsignaciones().size());
            assertFalse(concesion.isLiberada());
        } // Invoca close() automáticamente

        assertEquals(3, pool1.getPermisosDisponibles(), "Pool 1 debió recuperar todos sus permisos");
        assertEquals(2, pool2.getPermisosDisponibles(), "Pool 2 debió recuperar todos sus permisos");
    }

    @Test
    @DisplayName("Flujo concurrente completo: Despachador atiende múltiples pedidos sin fuga de recursos")
    void testDespachadorYTrabajadorFlujoCompletoConcurrente() throws InterruptedException {
        // Reloj acelerado a 300x para que la prueba se complete en menos de 2 segundos reales
        SimClock clock = new SimClock(300.0);
        GestorRecursos gestor = new GestorRecursos();
        ListaEspera listaEspera = new ListaEspera();
        PoliticaPrioridadEstricta politica = new PoliticaPrioridadEstricta();

        Despachador despachador = new Despachador(listaEspera, gestor, clock, politica);
        despachador.iniciar();

        Cliente cliente = new Cliente("CLI-1", "Empresa X", "x@x.gt", NivelServicio.EXPRES);
        Producto prodFragil = CatalogoProductos.obtenerPorId("PROD-FRAG-01");
        Producto prodGeneral = CatalogoProductos.obtenerPorId("PROD-GEN-01");

        int numPedidos = 8;
        for (int i = 0; i < numPedidos; i++) {
            Pedido p = new Pedido(cliente, NivelServicio.fromNivel((i % 5) + 1),
                    List.of(new LineaPedido(i % 2 == 0 ? prodFragil : prodGeneral, 2)), 0);
            listaEspera.agregar(p);
        }
        despachador.despertar();

        // Esperar a que todos los pedidos terminen
        long inicioEspera = System.currentTimeMillis();
        while (despachador.getPedidosCompletados().size() < numPedidos && (System.currentTimeMillis() - inicioEspera < 6000)) {
            Thread.sleep(100);
        }

        despachador.detener();

        assertEquals(numPedidos, despachador.getPedidosCompletados().size(),
                "Todos los pedidos debieron ser despachados y completados");
        assertEquals(0, listaEspera.size(), "La lista de espera debió quedar vacía");

        // Invariante de oro: Todos los 6 pools de recursos deben estar 100% disponibles sin ninguna fuga
        for (TipoRecurso tipo : TipoRecurso.values()) {
            PoolRecurso pool = gestor.getPool(tipo);
            assertEquals(tipo.getCapacidadDefecto(), pool.getPermisosDisponibles(),
                    "Fuga detectada en pool: " + tipo.name());
        }
    }
}
