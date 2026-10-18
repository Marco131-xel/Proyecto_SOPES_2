package gt.usac.redxela.almacen;

import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.estadisticas.Estadisticas;
import gt.usac.redxela.inventario.Inventario;
import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.Despachador;
import gt.usac.redxela.planificacion.ListaEspera;
import gt.usac.redxela.planificacion.PoliticaPrioridadEstricta;
import gt.usac.redxela.recepcion.Camion;
import gt.usac.redxela.recepcion.Recepcion;
import gt.usac.redxela.recursos.GestorRecursos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de aceptación para la Fase 4:
 * Almacenamiento no contiguo (R13), Productor-Consumidor con contrapresión (R10),
 * prevención de sobreventa y despertar reactivo de pedidos en espera de stock (R12).
 */
class Fase4AlmacenEInventarioTest {

    private Producto prodFragil;
    private Producto prodGeneral;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        Pedido.reiniciarSecuencialParaPruebas();
        prodFragil = CatalogoProductos.obtenerPorId("PROD-FRAG-01"); // 8 u/celda
        prodGeneral = CatalogoProductos.obtenerPorId("PROD-GEN-01"); // 20 u/celda
        cliente = new Cliente("CLI-TEST", "Cliente Prueba", "test@test.gt", NivelServicio.EXPRES);
    }

    @Test
    @DisplayName("Almacén: Asignación indexada no contigua y consistencia de mapa de bits (R13)")
    void testAsignacionNoContiguaYConsistenciaBitmap() {
        Almacen almacen = new Almacen();
        assertEquals(240, almacen.getUbicacionesLibres());

        // Lote 1: 20 unidades de prodFragil (8 u/celda -> 3 celdas: 8 + 8 + 4)
        Optional<Lote> lote1Opt = almacen.intentarAsignarLote("LOT-1", prodFragil, 20, 0);
        assertTrue(lote1Opt.isPresent());
        Lote lote1 = lote1Opt.get();
        assertEquals(3, lote1.getTablaAsignacion().size());
        assertEquals(237, almacen.getUbicacionesLibres());
        assertEquals(4, lote1.calcularFragmentacionInterna()); // 8 - 4 = 4 unidades vacías en última celda

        // Lote 2: 10 unidades de prodGeneral (20 u/celda -> 1 celda: 10)
        Optional<Lote> lote2Opt = almacen.intentarAsignarLote("LOT-2", prodGeneral, 10, 0);
        assertTrue(lote2Opt.isPresent());
        assertEquals(236, almacen.getUbicacionesLibres());

        // Liberamos Lote 1 (celdas 0, 1, 2 vuelven al mapa de bits) mientras Lote 2 sigue en celda 3
        almacen.liberarLoteCompleto("LOT-1");
        assertEquals(239, almacen.getUbicacionesLibres());

        // Asignamos Lote 3 con 30 unidades de prodFragil (requiere 4 celdas)
        // Tomará las celdas 0, 1, 2 y luego SALTARÁ la celda 3 (ocupada por Lote 2) y tomará la celda 4!
        // ¡Esto demuestra la asignación NO CONTIGUA sin fragmentación externa!
        Optional<Lote> lote3Opt = almacen.intentarAsignarLote("LOT-3", prodFragil, 30, 0);
        assertTrue(lote3Opt.isPresent());
        Lote lote3 = lote3Opt.get();
        assertEquals(4, lote3.getTablaAsignacion().size());

        List<Ubicacion> ubiLote3 = lote3.getUbicaciones();
        assertEquals(0, ubiLote3.get(0).getIndiceLineal());
        assertEquals(1, ubiLote3.get(1).getIndiceLineal());
        assertEquals(2, ubiLote3.get(2).getIndiceLineal());
        assertEquals(4, ubiLote3.get(3).getIndiceLineal(), "La 4ta celda debió ser la 4 (salteando la celda 3 ocupada)");
    }

    @Test
    @DisplayName("Productor-Consumidor: Recepción más rápida que Inventario genera contrapresión sin pérdida (R10)")
    void testProductorConsumidorContrapresionSinPerdida() throws InterruptedException {
        GestorRecursos gestorRecursos = new GestorRecursos();
        SimClock clock = new SimClock(500.0);
        Almacen almacen = new Almacen();
        Recepcion recepcion = new Recepcion(gestorRecursos, clock);
        Inventario inventario = new Inventario(almacen, recepcion, clock, null);

        recepcion.iniciar();
        // NOTA: NO iniciamos el hilo de inventario de inmediato para forzar la saturación de la cola de recepción

        int totalCamiones = 25; // Supera la capacidad de la cola acotada (20)
        for (int i = 0; i < totalCamiones; i++) {
            recepcion.registrarLlegada(prodGeneral, 10);
        }

        // Esperar a que la cola acotada se llene al tope (20)
        long limite = System.currentTimeMillis() + 3000;
        while (recepcion.getColaMensajes().size() < 20 && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }

        // Verificar contrapresión activa: La cola está al 100% (20) y los otros 5 camiones esperan en el patio
        assertEquals(20, recepcion.getColaMensajes().size(), "La cola de mensajes debe estar saturada a 20");
        assertTrue(recepcion.snapshot().camionesEnPatio() >= 4, "Los camiones excedentes deben retenerse en el patio");

        // Ahora iniciamos el consumidor (Inventario) para drenar la cola
        inventario.iniciar();

        // Esperar a que se procesen los 25 mensajes completos
        limite = System.currentTimeMillis() + 5000;
        while (inventario.snapshot().totalMensajesProcesados() < totalCamiones && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }

        recepcion.detener();
        inventario.detener();

        assertEquals(totalCamiones, inventario.snapshot().totalMensajesProcesados(),
                "Todos los 25 mensajes deben procesarse sin pérdida de datos ni bloqueo permanente");
        assertEquals(250, inventario.getExistenciaFisica(prodGeneral.id()),
                "25 camiones de 10 unidades deben sumar exactamente 250 unidades en inventario");
    }

    @Test
    @DisplayName("Prevención de Sobreventa: Reservas concurrentes compitiendo por stock evitan condición de carrera")
    void testPrevencionSobreventaConcurrente() throws InterruptedException {
        GestorRecursos gestorRecursos = new GestorRecursos();
        SimClock clock = new SimClock(100.0);
        Almacen almacen = new Almacen();
        Recepcion recepcion = new Recepcion(gestorRecursos, clock);
        Inventario inventario = new Inventario(almacen, recepcion, clock, null);

        // Almacenar manualmente un lote de exactamente 10 unidades
        almacen.intentarAsignarLote("LOT-INICIAL", prodGeneral, 10, 0);
        // Ingresar 10 unidades a las existencias
        recepcion.getColaMensajes().put(new gt.usac.redxela.recepcion.MensajeRecepcion("CAM-0", prodGeneral.id(), 10, 0));
        inventario.iniciar();

        while (inventario.getExistenciaFisica(prodGeneral.id()) < 10) {
            Thread.sleep(20);
        }
        inventario.detener();

        assertEquals(10, inventario.getDisponible(prodGeneral.id()));

        // 30 hilos compiten simultáneamente por reservar 1 unidad cada uno
        int numHilos = 30;
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latchFin = new CountDownLatch(numHilos);

        AtomicInteger reservasExitosas = new AtomicInteger(0);
        AtomicInteger reservasFallidas = new AtomicInteger(0);

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    Pedido p = new Pedido(cliente, NivelServicio.EXPRES, List.of(new LineaPedido(prodGeneral, 1)), 0);
                    boolean exito = inventario.intentarReservarStock(p);
                    if (exito) {
                        reservasExitosas.incrementAndGet();
                    } else {
                        reservasFallidas.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latchFin.countDown();
                }
            });
        }

        latch.countDown();
        assertTrue(latchFin.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        // Exactamente 10 deben haber ganado la reserva y 20 deben haber sido rechazadas
        assertEquals(10, reservasExitosas.get(), "No debe haber sobreventa de unidades");
        assertEquals(20, reservasFallidas.get());
        assertEquals(0, inventario.getDisponible(prodGeneral.id()), "El stock libre debe ser 0");
        assertEquals(10, inventario.getReservado(prodGeneral.id()), "El stock reservado debe ser exactamente 10");
    }

    @Test
    @DisplayName("Despertar reactivo: Llegada de stock desbloquea y despacha pedidos en EN_ESPERA_STOCK (R12)")
    void testLlegadaStockDespiertaPedidosEnEspera() throws InterruptedException {
        SimClock clock = new SimClock(400.0);
        GestorRecursos gestor = new GestorRecursos();
        Almacen almacen = new Almacen();
        Recepcion recepcion = new Recepcion(gestor, clock);
        ListaEspera listaEspera = new ListaEspera();
        PoliticaPrioridadEstricta politica = new PoliticaPrioridadEstricta();
        Estadisticas stats = new Estadisticas();

        Despachador despachador = new Despachador(listaEspera, gestor, clock, politica, stats);
        Inventario inventario = new Inventario(almacen, recepcion, clock, despachador);
        despachador.setInventario(inventario);

        despachador.iniciar();
        recepcion.iniciar();
        inventario.iniciar();

        // Creamos un pedido de prodFragil (sin existencias en almacén aún)
        Pedido pedido = new Pedido(cliente, NivelServicio.EXPRES, List.of(new LineaPedido(prodFragil, 4)), 0);
        listaEspera.agregar(pedido);
        despachador.despertar();

        // Esperar a que el despachador lo evalúe: debe quedar en EN_ESPERA_STOCK
        Thread.sleep(200);
        assertEquals(EstadoPedido.EN_ESPERA_STOCK, pedido.getEstado(),
                "El pedido debe quedar en EN_ESPERA_STOCK por falta de existencias físicas");

        // Ahora arriba un camión con 16 unidades de prodFragil
        recepcion.registrarLlegada(prodFragil, 16);

        // Esperar a que el camión sea escaneado, enviado, inventariado, despierte al despachador
        // y el pedido sea completado automáticamente
        long limite = System.currentTimeMillis() + 6000;
        while (pedido.getEstado() != EstadoPedido.COMPLETADO && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }

        recepcion.detener();
        inventario.detener();
        despachador.detener();

        assertEquals(EstadoPedido.COMPLETADO, pedido.getEstado(),
                "El pedido en espera de stock debió arrancar automáticamente y completarse tras la llegada del camión");
        assertEquals(1, despachador.getPedidosCompletados().size());
    }
}
