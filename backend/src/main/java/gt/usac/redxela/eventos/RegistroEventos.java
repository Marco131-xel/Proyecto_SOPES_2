package gt.usac.redxela.eventos;

import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.config.ConfiguracionSimulacion;
import org.springframework.stereotype.Component;
import java.util.ArrayDeque;
import java.util.List;

/** Historial acotado: conserva eventos reales sin crecer con la cantidad de pedidos. */
@Component
public class RegistroEventos {
    private final SimClock reloj;
    private final ArrayDeque<Evento> eventos = new ArrayDeque<>();
    private long secuencia;

    public RegistroEventos(SimClock reloj) { this.reloj = reloj; }

    /** El monitor protege la secuencia y el límite al recibir eventos de varios hilos. */
    public synchronized void registrar(String tipo, String mensaje) {
        eventos.addFirst(new Evento(++secuencia, reloj.ahoraSimuladoSegundos(), tipo, mensaje));
        while (eventos.size() > ConfiguracionSimulacion.MAX_EVENTOS_HISTORICO) eventos.removeLast();
    }

    public synchronized List<Evento> snapshot() { return List.copyOf(eventos); }

    public record Evento(long id, long instanteSimulado, String tipo, String mensaje) {}
}
