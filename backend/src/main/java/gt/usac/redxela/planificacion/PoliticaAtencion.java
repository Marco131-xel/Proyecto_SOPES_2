package gt.usac.redxela.planificacion;

import gt.usac.redxela.modelo.Pedido;

import java.util.Comparator;

/**
 * Interfaz de estrategia para los algoritmos de planificación (Scheduling Policies)
 * del centro de distribución REDXela (Patrón Strategy).
 * 
 * Permite intercambiar en caliente la política de selección de procesos en la lista
 * de espera para comparar métricas de latencia, inanición y cumplimiento de SLA.
 */
public interface PoliticaAtencion {

    /**
     * Nombre formal de la política de planificación.
     */
    String nombre();

    /**
     * Descripción técnica del criterio de ordenamiento utilizado para la interfaz.
     */
    String descripcion();

    /**
     * Genera un comparador dinámico para ordenar la cola de espera de procesos
     * en función del instante actual simulado (esencial para algoritmos dependientes
     * del tiempo como Aging o EDF).
     * 
     * @param ahoraSimulado Instante de simulación actual en segundos.
     * @return Comparador de pedidos según la política.
     */
    Comparator<Pedido> comparador(long ahoraSimulado);
}
