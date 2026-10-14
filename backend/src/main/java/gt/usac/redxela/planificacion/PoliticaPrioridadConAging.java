package gt.usac.redxela.planificacion;

import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.modelo.Pedido;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Política de Planificación por Prioridad con Envejecimiento (Priority Scheduling with Aging).
 * 
 * Resuelve el problema fundamental de la INANICIÓN (Starvation) presente en la política
 * de prioridad estricta: los pedidos de nivel bajo (Nivel 4 Programado y Nivel 5 Económico)
 * podrían quedar indefinidamente relegados si la tasa de llegada de pedidos de Nivel 1 y 2
 * es continua.
 * 
 * Fundamento Teórico:
 * Cada proceso envejece con el paso del tiempo en la cola de espera.
 * Su prioridad efectiva se recalcula dinámicamente como:
 *   NivelEfectivo = max(1, NivelOriginal - floor(EsperaActual / T_aging))
 * 
 * Conforme EsperaActual crece, el proceso avanza hacia Nivel 1 (Exprés), garantizando
 * matemáticamente que ningún pedido sufrirá inanición indefinida.
 */
@Component
public class PoliticaPrioridadConAging implements PoliticaAtencion {

    private final long tAgingSegundos;

    public PoliticaPrioridadConAging() {
        this(ConfiguracionSimulacion.T_AGING_SEGUNDOS_SIM);
    }

    public PoliticaPrioridadConAging(long tAgingSegundos) {
        if (tAgingSegundos <= 0) {
            throw new IllegalArgumentException("El período de envejecimiento debe ser mayor a 0");
        }
        this.tAgingSegundos = tAgingSegundos;
    }

    /**
     * Calcula el nivel efectivo de servicio de un pedido en un instante dado.
     * 
     * @param p Pedido en evaluación.
     * @param ahoraSimulado Instante actual en segundos simulados.
     * @return Nivel efectivo entre 1 (prioridad máxima) y 5.
     */
    public int calcularNivelEfectivo(Pedido p, long ahoraSimulado) {
        long esperaSegundos = p.calcularEsperaSegundos(ahoraSimulado);
        int promociones = (int) (esperaSegundos / tAgingSegundos);
        return Math.max(1, p.getNivel().getNivel() - promociones);
    }

    @Override
    public String nombre() {
        return "PRIORIDAD_CON_AGING";
    }

    @Override
    public String descripcion() {
        return String.format("Prioridad dinámica con envejecimiento cada %d s simulados (evita inanición)", tAgingSegundos);
    }

    @Override
    public Comparator<Pedido> comparador(long ahoraSimulado) {
        return (p1, p2) -> {
            int nivelEfectivo1 = calcularNivelEfectivo(p1, ahoraSimulado);
            int nivelEfectivo2 = calcularNivelEfectivo(p2, ahoraSimulado);

            int cmpNivel = Integer.compare(nivelEfectivo1, nivelEfectivo2);
            if (cmpNivel != 0) {
                return cmpNivel;
            }

            // Desempate por orden de llegada original (FIFO)
            int cmpLlegada = Long.compare(p1.getInstanteCreacion(), p2.getInstanteCreacion());
            if (cmpLlegada != 0) {
                return cmpLlegada;
            }

            return p1.getId().compareTo(p2.getId());
        };
    }

    public long getTAgingSegundos() {
        return tAgingSegundos;
    }
}
