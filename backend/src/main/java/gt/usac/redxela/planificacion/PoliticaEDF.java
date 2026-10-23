package gt.usac.redxela.planificacion;

import gt.usac.redxela.modelo.Pedido;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Política de Planificación por Fecha Límite Más Cercana (Earliest Deadline First - EDF).
 * 
 * En sistemas en tiempo real y centros de distribución con acuerdos de nivel de servicio (SLA),
 * cada pedido tiene un plazo máximo de espera objetivo antes de incurrir en penalización.
 * El deadline absoluto se calcula como:
 *   Deadline = InstanteCreacion + EsperaObjetivoSegundos(Nivel)
 * 
 * Fundamento Teórico:
 * EDF ordena por el vencimiento absoluto del SLA. Las garantías clásicas de
 * optimalidad requieren supuestos como expropiación y recursos independientes.
 * Este simulador retiene varios recursos y no expropia: no se promete maximizar
 * el porcentaje de SLA cumplido; el comparador mide ese resultado experimental.
 */
@Component
public class PoliticaEDF implements PoliticaAtencion {

    @Override
    public String nombre() {
        return "EDF";
    }

    @Override
    public String descripcion() {
        return "Fecha límite más cercana (Earliest Deadline First - menor tiempo restante de SLA)";
    }

    @Override
    public Comparator<Pedido> comparador(long ahoraSimulado) {
        return Comparator
                .comparingLong(Pedido::getVenceSLA)
                .thenComparingInt((Pedido p) -> p.getNivel().getNivel())
                .thenComparingLong(Pedido::getInstanteCreacion)
                .thenComparing(Pedido::getId);
    }
}
