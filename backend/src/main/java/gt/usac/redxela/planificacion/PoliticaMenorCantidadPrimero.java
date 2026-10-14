package gt.usac.redxela.planificacion;

import gt.usac.redxela.modelo.Pedido;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Política de Planificación por Menor Cantidad de Artículos Primero (Shortest Job First - SJF).
 * 
 * En el centro de distribución REDXela, el tiempo de procesamiento de cada pedido es
 * estrictamente proporcional a la suma total de unidades de sus líneas (R6).
 * Por ende, seleccionar el pedido con menor número de unidades equivale al algoritmo
 * clásico SJF (o SPT - Shortest Processing Time).
 * 
 * Fundamento Teórico:
 * El algoritmo SJF es matemáticamente óptimo para minimizar el tiempo medio de espera
 * (Average Waiting Time) en sistemas de colas.
 * 
 * Riesgo Teórico:
 * Pedidos muy grandes (alta cantidad de unidades) pueden sufrir inanición si entran
 * constantemente pedidos pequeños. Como desempate se utiliza el nivel de servicio
 * y posteriormente la hora de llegada (FIFO).
 */
@Component
public class PoliticaMenorCantidadPrimero implements PoliticaAtencion {

    @Override
    public String nombre() {
        return "MENOR_CANTIDAD_PRIMERO";
    }

    @Override
    public String descripcion() {
        return "Menor cantidad de unidades primero (SJF / menor tiempo de procesamiento estimado)";
    }

    @Override
    public Comparator<Pedido> comparador(long ahoraSimulado) {
        return Comparator
                .comparingInt(Pedido::getTotalUnidades)
                .thenComparingInt((Pedido p) -> p.getNivel().getNivel())
                .thenComparingLong(Pedido::getInstanteCreacion)
                .thenComparing(Pedido::getId);
    }
}
