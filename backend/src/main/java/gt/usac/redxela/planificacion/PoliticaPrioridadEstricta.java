package gt.usac.redxela.planificacion;

import gt.usac.redxela.modelo.Pedido;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Política de Planificación por Prioridad Estricta (Strict Priority Scheduling).
 * 
 * Atiende primero a los pedidos de mayor prioridad (Nivel 1: Exprés -> Nivel 5: Económico).
 * En caso de empate en el nivel de servicio, utiliza orden FIFO (primer llegado, primer servido)
 * según el instante de creación.
 * 
 * Fundamento Teórico:
 * Algoritmo con prioridad estática. Si la tasa de llegada de pedidos de niveles 1 y 2
 * es alta, los niveles inferiores (especialmente Nivel 5) sufrirán INANICIÓN (starvation),
 * requiriendo mecanismos correctivos como Aging en políticas posteriores.
 */
@Component
public class PoliticaPrioridadEstricta implements PoliticaAtencion {

    @Override
    public String nombre() {
        return "PRIORIDAD_ESTRICTA";
    }

    @Override
    public String descripcion() {
        return "Nivel de servicio (1 Exprés a 5 Económico); desempate por orden de llegada (FIFO)";
    }

    @Override
    public Comparator<Pedido> comparador(long ahoraSimulado) {
        return Comparator
                .comparingInt((Pedido p) -> p.getNivel().getNivel())
                .thenComparingLong(Pedido::getInstanteCreacion)
                .thenComparing(Pedido::getId);
    }
}
