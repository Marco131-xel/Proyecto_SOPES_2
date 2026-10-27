package gt.usac.redxela.api;

import gt.usac.redxela.api.dto.EstadoSistema;
import gt.usac.redxela.eventos.RegistroEventos;
import gt.usac.redxela.excepciones.ManejadorGlobalExcepciones;
import gt.usac.redxela.modelo.GestorClientes;
import gt.usac.redxela.proceso.CoordinadorSimulacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regresiones del cierre SSE: aislamiento de clientes y respuesta ya inutilizable. */
class SseDesconexionTest {
    private SimulacionController controller;
    private List<SseEmitter> emisores;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        CoordinadorSimulacion motor = mock(CoordinadorSimulacion.class);
        when(motor.capturarEstado()).thenReturn(new EstadoSistema(
                0, 1, false, "EDF", "EDF", true, false, false, 4,
                Map.of(), List.of(), List.of(), List.of(), null, null, null, null, null, List.of()));
        controller = new SimulacionController(motor, mock(GestorClientes.class), mock(RegistroEventos.class));
        // Se invoca el publicador explícitamente para no depender del reloj del scheduler.
        ((ScheduledExecutorService) ReflectionTestUtils.getField(controller, "schedulerSSE")).shutdownNow();
        emisores = (List<SseEmitter>) ReflectionTestUtils.getField(controller, "emisoresSSE");
    }

    @AfterEach
    void cerrar() {
        controller.cerrar();
    }

    static Stream<Exception> fallosDeConexion() {
        return Stream.of(new IOException("Broken pipe"),
                new AsyncRequestNotUsableException("Response not usable after response errors"),
                new IllegalStateException("AsyncContext already closed"));
    }

    @ParameterizedTest
    @MethodSource("fallosDeConexion")
    void clienteDesconectadoNoImpideEnviarALosDemasNiSeCompletaOtraVez(Exception fallo) throws Exception {
        SseEmitter roto = mock(SseEmitter.class);
        SseEmitter activo = mock(SseEmitter.class);
        doThrow(fallo).when(roto).send(any(SseEmitter.SseEventBuilder.class));
        // Si se intenta completar la conexión rota, reproduce la excepción de Tomcat.
        doThrow(new IllegalStateException("AsyncContext no disponible")).when(roto).complete();
        emisores.add(roto);
        emisores.add(activo);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(controller, "transmitirEstadoSSE"));
        assertEquals(List.of(activo), emisores);
        verify(roto, never()).complete();
        verify(roto, never()).completeWithError(any());
        verify(activo).send(any(SseEmitter.SseEventBuilder.class));

        SseEmitter reconectado = controller.conectarStreamSSE();
        assertTrue(emisores.contains(reconectado));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(controller, "transmitirEstadoSSE"));
        verify(roto, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(activo, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void apagadoContinuaAunqueUnEmisorYaEsteCerrado() {
        SseEmitter roto = mock(SseEmitter.class);
        SseEmitter activo = mock(SseEmitter.class);
        doThrow(new IllegalStateException("AsyncContext no disponible")).when(roto).complete();
        emisores.add(roto);
        emisores.add(activo);

        assertDoesNotThrow(controller::cerrar);
        verify(activo).complete();
        assertTrue(emisores.isEmpty());
    }

    @RestController
    static class ApiDePrueba {
        @GetMapping(value = "/sse-cerrado", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        String cerrado() throws IOException {
            throw new AsyncRequestNotUsableException("Response not usable after response errors");
        }

        @GetMapping(value = "/sse-abortado", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        String abortado() throws IOException {
            throw new IOException("Broken pipe");
        }

        @GetMapping("/conflicto")
        String conflicto() {
            throw new IllegalStateException("Conflicto de dominio");
        }
    }

    @Test
    void desconexionesNoIntentanEscribirJsonPeroConflictosSiguenDevolviendo409() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApiDePrueba())
                .setControllerAdvice(new ManejadorGlobalExcepciones()).build();
        for (String ruta : List.of("/sse-cerrado", "/sse-abortado")) {
            mvc.perform(get(ruta).accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(content().string(""))
                    .andExpect(result -> assertNull(result.getResponse().getContentType()));
        }
        mvc.perform(get("/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensaje").value("Conflicto de dominio"));
    }
}
