package gt.usac.redxela.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.eventos.RegistroEventos;
import gt.usac.redxela.modelo.*;
import gt.usac.redxela.proceso.CoordinadorSimulacion;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verifica el contrato HTTP contra el motor real, incluyendo SSE y recuperación del bloqueo. */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Fase6ApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CoordinadorSimulacion motor;
    @Autowired GestorClientes clientes;
    @Autowired RegistroEventos eventos;

    private JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString());
    }

    /** Espera una condición del motor con plazo, sin depender de una pausa fija para acertar. */
    private void esperar(BooleanSupplier condicion) throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            while (!condicion.getAsBoolean()) Thread.sleep(20);
        });
    }

    @Test @Order(1)
    void contratosDeClientesCatalogoEstadoYSse() throws Exception {
        esperar(() -> motor.getInventario().snapshot().totalMensajesProcesados() >= 18);
        mvc.perform(get("/api/clientes")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nivelContratado.nivel").isNumber())
                .andExpect(jsonPath("$[0].nivelContratado.nombre").isString());
        mvc.perform(get("/api/catalogo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.productos.length()").value(18));
        mvc.perform(get("/api/estado")).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos.ESCANER.capacidadTotal").value(1))
                .andExpect(jsonPath("$.almacen.totalUbicaciones").value(240))
                .andExpect(jsonPath("$.eventos").isArray());
        MvcResult stream = mvc.perform(get("/api/estado/stream"))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        assertTrue(stream.getResponse().getContentAsString().contains("event:estado"));
        assertTrue(stream.getResponse().getContentAsString().contains("tiempoSimuladoSegundos"));
        stream.getRequest().getAsyncContext().complete();
    }

    @Test @Order(2)
    void validacionesDevuelven400404Y409() throws Exception {
        mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Cliente\",\"correo\":\"cliente@example.com\",\"nivelServicio\":6}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"inexistente\",\"lineas\":[]}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/simulacion/velocidad").contentType(MediaType.APPLICATION_JSON).content("{\"factor\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/generador/config").contentType(MediaType.APPLICATION_JSON).content("{\"tasaPedidosPorMinuto\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/prevencion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/conflicto/resolver").contentType(MediaType.APPLICATION_JSON).content("{\"ganadorId\":\"PED-X\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"CLI-001\",\"lineas\":[{\"productoId\":\"PROD-PES-01\",\"cantidad\":1}],\"ordenAdquisicionPersonalizado\":[\"CARGA\",\"CARGA\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(3)
    void clientePedidoRecepcionYPoliticaFuncionanConElMotor() throws Exception {
        JsonNode cliente = cuerpo(mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Cliente API\",\"correo\":\"cliente@example.com\",\"nivelServicio\":3}"))
                .andExpect(status().isCreated()).andReturn());
        assertEquals(3, cliente.path("nivelContratado").path("nivel").asInt());
        mvc.perform(put("/api/simulacion/velocidad").contentType(MediaType.APPLICATION_JSON).content("{\"factor\":600}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/politica").contentType(MediaType.APPLICATION_JSON).content("{\"politica\":\"EDF\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.politicaActiva").value("EDF"));
        String pedido = cuerpo(mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"" + cliente.path("id").asText() + "\",\"lineas\":[{\"productoId\":\"PROD-PES-01\",\"cantidad\":1}]}"))
                .andExpect(status().isAccepted()).andReturn()).path("pedidoId").asText();
        esperar(() -> motor.getDespachador().getPedidosCompletados().stream().anyMatch(p -> p.getId().equals(pedido)));
        int previo = motor.getInventario().getExistenciaFisica("PROD-PES-01");
        mvc.perform(post("/api/recepcion").contentType(MediaType.APPLICATION_JSON).content("{\"productoId\":\"PROD-PES-01\",\"cantidad\":3}"))
                .andExpect(status().isAccepted());
        esperar(() -> motor.getInventario().getExistenciaFisica("PROD-PES-01") == previo + 3);
        assertTrue(eventos.snapshot().stream().anyMatch(e -> e.tipo().equals("COMPLETADO") && e.mensaje().contains(pedido)));
    }

    @Test @Order(4)
    void lineasRepetidasNoSobrevendenStock() {
        Producto producto = CatalogoProductos.obtenerPorId("PROD-PES-01");
        int disponible = motor.getInventario().getDisponible(producto.id());
        Pedido repetido = new Pedido(clientes.obtenerPorId("CLI-001"), null,
                List.of(new LineaPedido(producto, disponible), new LineaPedido(producto, disponible)), 0);
        assertFalse(motor.getInventario().intentarReservarStock(repetido));
        assertEquals(0, motor.getInventario().getReservado(producto.id()));
    }

    @Test @Order(5)
    void escenarioSeDetectaYSeResuelveDesdeHttp() throws Exception {
        esperar(() -> motor.getDespachador().getPedidosEnProceso().isEmpty());
        JsonNode respuesta = cuerpo(mvc.perform(post("/api/escenarios/deadlock"))
                .andExpect(status().isOk()).andReturn());
        esperar(() -> motor.getDetectorDeadlock().getUltimoInforme().hayConflicto());
        mvc.perform(get("/api/conflicto")).andExpect(status().isOk()).andExpect(jsonPath("$.hayConflicto").value(true));
        String ganador = respuesta.path("pedidosInvolucrados").get(0).asText();
        String otro = respuesta.path("pedidosInvolucrados").get(1).asText();
        mvc.perform(post("/api/conflicto/resolver").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ganadorId\":\"" + ganador + "\"}"))
                .andExpect(status().isOk());
        esperar(() -> !motor.getDetectorDeadlock().ejecutarDeteccion().hayConflicto());
        mvc.perform(put("/api/prevencion").contentType(MediaType.APPLICATION_JSON).content("{\"activar\":true}"))
                .andExpect(status().isOk());
        esperar(() -> motor.getDespachador().getPedidosCompletados().stream().filter(p -> p.getId().equals(ganador) || p.getId().equals(otro)).count() == 2);
        assertTrue(motor.getGestorRecursos().snapshot().values().stream().flatMap(p -> p.unidades().stream()).noneMatch(u -> u.enMantenimiento()));
    }

    @Test @Order(6)
    void generadorIncrementaContadoresEHistorialAcotado() throws Exception {
        long previo = motor.capturarEstado().estadisticas().pedidosCreados();
        mvc.perform(post("/api/generador/iniciar")).andExpect(status().isOk());
        esperar(() -> motor.capturarEstado().estadisticas().pedidosCreados() > previo);
        mvc.perform(post("/api/generador/detener")).andExpect(status().isOk());
        for (int i = 0; i < 150; i++) eventos.registrar("PRUEBA", "Evento " + i);
        assertEquals(100, eventos.snapshot().size());
        assertThrows(UnsupportedOperationException.class, () -> eventos.snapshot().clear());
    }

    @Test @Order(7)
    void snapshotsDeLotesNoConservanCeldasLiberadasOReasignadas() {
        var almacen = new gt.usac.redxela.almacen.Almacen();
        Producto producto = CatalogoProductos.obtenerPorId("PROD-PES-01");
        var lote = almacen.intentarAsignarLote("ANTERIOR", producto, 2, 0).orElseThrow();
        var celda = lote.getUbicaciones().getFirst();
        celda.asignar(producto, 1, lote.getId());
        assertEquals(1, almacen.snapshot(0).lotesActivos().getFirst().cantidadTotal());
        almacen.liberarUbicacion(celda);
        assertTrue(almacen.snapshot(0).lotesActivos().isEmpty());
        almacen.intentarAsignarLote("NUEVO", producto, 2, 1).orElseThrow();
        almacen.liberarLoteCompleto("ANTERIOR");
        assertEquals("NUEVO", celda.getLoteIdActual());
        assertTrue(lote.snapshot().tablaAsignacion().isEmpty());
    }

    @Test @Order(8)
    void erroresDeProtocoloNoSeConviertenEn500() throws Exception {
        mvc.perform(get("/api/no-existe")).andExpect(status().isNotFound());
        mvc.perform(put("/api/clientes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/clientes").contentType(MediaType.TEXT_PLAIN).content("cliente"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test @Order(9)
    void pausaDelRelojDetieneElTrabajoEnCurso() throws Exception {
        SimClock reloj = new SimClock(600);
        reloj.pausar();
        CompletableFuture<Void> trabajo = CompletableFuture.runAsync(() -> {
            try { reloj.dormirSimuladoMs(600); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            Thread.sleep(150);
            assertFalse(trabajo.isDone(), "El trabajo debe esperar mientras el reloj está pausado");
        } finally { reloj.reanudar(); }
        trabajo.get(2, java.util.concurrent.TimeUnit.SECONDS);
    }
}
