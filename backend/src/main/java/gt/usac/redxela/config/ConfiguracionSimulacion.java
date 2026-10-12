package gt.usac.redxela.config;

/**
 * Centraliza todos los parámetros y constantes operativas de la simulación.
 * 
 * Evita la dispersión de números mágicos en el código y permite ajustar
 * capacidades, tiempos y dimensiones del centro de distribución REDXela.
 */
public final class ConfiguracionSimulacion {

    private ConfiguracionSimulacion() {
        // Clase de constantes, no instanciable
    }

    // --- Capacidades fijas de recursos compartidos (según enunciado) ---
    public static final int CAPACIDAD_ENCARGADOS = 10;
    public static final int CAPACIDAD_ESTACIONES_EMPAQUE = 6;
    public static final int CAPACIDAD_AREAS_CARGA = 2;
    public static final int CAPACIDAD_MONTACARGAS = 3;
    public static final int CAPACIDAD_CONTROL_CALIDAD = 4;
    public static final int CAPACIDAD_ESCANER = 1; // Exclusión mutua estricta

    // --- Dimensiones del Almacén (Capacidad definida: 6 x 4 x 10 = 240 ubicaciones) ---
    public static final int ALMACEN_PASILLOS = 6;
    public static final int ALMACEN_NIVELES = 4;
    public static final int ALMACEN_POSICIONES = 10;
    public static final int ALMACEN_TOTAL_UBICACIONES = ALMACEN_PASILLOS * ALMACEN_NIVELES * ALMACEN_POSICIONES;

    // --- Parámetros de Tiempo y Simulación ---
    /** Factor por defecto: 1 segundo real = 60 segundos simulados (1 minuto simulado). */
    public static final double FACTOR_RELOJ_POR_DEFECTO = 60.0;
    public static final double FACTOR_RELOJ_MINIMO = 1.0;
    public static final double FACTOR_RELOJ_MAXIMO = 600.0;

    /** Segundos simulados que toma procesar una unidad de producto en un pedido. */
    public static final long SEGUNDOS_SIM_POR_UNIDAD = 30;

    /** Intervalo en milisegundos reales entre actualizaciones de progreso (ticks del pedido). */
    public static final long INTERVALO_TICK_PROGRESO_MS = 100;

    // --- Parámetros de Colas y Buffers ---
    /** Capacidad máxima de la cola acotada de mensajes Recepción -> Inventario. */
    public static final int CAPACIDAD_COLA_RECEPCION = 20;

    /** Ventana máxima de pedidos completados retenidos en memoria para la UI. */
    public static final int MAX_PEDIDOS_COMPLETADOS_HISTORICO = 200;

    // --- Parámetros de Deadlock ---
    /** Pausa en milisegundos reales entre adquisiciones sucesivas en modo SIN PREVENCIÓN. */
    public static final long PAUSA_ENTRE_ADQUISICIONES_MS = 1500;

    /** Intervalo de ejecución en milisegundos reales del hilo detector de deadlock. */
    public static final long INTERVALO_DETECCION_DEADLOCK_MS = 500;

    // --- Parámetros de Políticas y Aging ---
    /** Tiempo base simulado para incremento de prioridad efectiva (envejecimiento). */
    public static final long T_AGING_SEGUNDOS_SIM = 600; // 10 minutos simulados
}
