package gt.usac.redxela.estadisticas;

/**
 * Representa una muestra puntual en el tiempo simulado de la longitud de colas.
 * 
 * Utilizada para generar series de tiempo históricas en los gráficos de la UI (Recharts).
 */
public record MuestraHistoricaCola(
        long instanteSimulado,
        int pedidosEnCola,
        int pedidosEnProceso
) {}
