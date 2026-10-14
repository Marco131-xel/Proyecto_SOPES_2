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
 * De acuerdo con el teorema de Liu y Layland (1973), EDF es un algoritmo óptimo de
 * planificación dinámica: si un conjunto de tareas con plazos definidos es planificable,
 * EDF encontrará un cronograma válido sin violar fechas límite.
 * 
 * Prioriza al pedido cuyo SLA esté más próximo a vencer (o ya vencido), maximizando
 * directamente la tasa porcentual de cumplimiento de SLA del centro logístico.
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
