package gt.usac.redxela.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.redxela.proceso.CoordinadorSimulacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP real mediante MockMvc; el comparador no recibe componentes del motor en vivo. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(30)
class Fase7ComparacionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CoordinadorSimulacion motor;

    private JsonNode consultar() throws Exception {
        return json.readTree(mvc.perform(get("/api/comparacion")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode completar() throws Exception {
        long plazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JsonNode estado;
        do {
            estado = consultar();
            if (!estado.path("estado").asText().equals("EJECUTANDO")) break;
            Thread.sleep(10);
        } while (System.nanoTime() < plazo);
        assertEquals("COMPLETADA", estado.path("estado").asText(), estado.toString());
        return estado.path("resultado");
    }

    @Test void postAsincronoGetReproducibleYAislamientoDelSistemaEnVivo() throws Exception {
        long plazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (motor.getInventario().snapshot().totalMensajesProcesados() < 18 && System.nanoTime() < plazo) Thread.sleep(10);
        assertEquals(18, motor.getInventario().snapshot().totalMensajesProcesados());
        motor.getSimClock().pausar();
        var antes = motor.capturarEstado();
        try {
            assertEquals("SIN_EJECUTAR", consultar().path("estado").asText());
            String solicitud = "{\"semilla\":42,\"cantidadPedidos\":40,\"tasaLlegadasPorMinuto\":4}";
            mvc.perform(post("/api/comparacion").contentType(MediaType.APPLICATION_JSON).content(solicitud))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("EJECUTANDO"));
            JsonNode uno = completar();
            assertEquals(4, uno.path("politicas").size());
            assertEquals(64, uno.path("huellaCarga").asText().length());
            for (JsonNode politica : uno.path("politicas")) assertEquals(40, politica.path("pedidosCompletados").asInt());
            mvc.perform(post("/api/comparacion").contentType(MediaType.APPLICATION_JSON).content(solicitud)).andExpect(status().isAccepted());
            assertEquals(uno, completar());
            var despues = motor.capturarEstado();
            assertEquals(antes.recursos(), despues.recursos());
            assertEquals(antes.inventario(), despues.inventario());
            assertEquals(antes.almacen(), despues.almacen());
            assertEquals(antes.politicaActiva(), despues.politicaActiva());
            assertEquals(antes.tiempoSimuladoSegundos(), despues.tiempoSimuladoSegundos());
            assertEquals(antes.estadisticas().pedidosCreados(), despues.estadisticas().pedidosCreados());
            assertEquals(antes.eventos(), despues.eventos());
        } finally { motor.getSimClock().reanudar(); }
    }

    @Test void validaLimitesYJson() throws Exception {
        for (String solicitud : new String[]{"{", "{\"cantidadPedidos\":0}", "{\"cantidadPedidos\":1001}", "{\"tasaLlegadasPorMinuto\":0}", "{\"tasaLlegadasPorMinuto\":121}"}) {
            mvc.perform(post("/api/comparacion").contentType(MediaType.APPLICATION_JSON).content(solicitud)).andExpect(status().isBadRequest());
        }
    }
}
