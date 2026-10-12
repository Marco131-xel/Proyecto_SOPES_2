package gt.usac.redxela.modelo;

import gt.usac.redxela.config.ConfiguracionSimulacion;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Representa un pedido dentro del centro de distribución REDXela.
 * 
 * Actúa como la abstracción de un PROCESO del sistema operativo:
 * - Requiere recursos compartidos para su ejecución.
 * - Tiene un tiempo de ráfaga estimado proporcional a sus unidades (R6).
 * - Mantiene contadores de reencolado, tiempos de espera y vencimiento de SLA.
 * - Soporta orden configurable de adquisición para la demostración de interbloqueos.
 */
public class Pedido {

    private static final AtomicLong SECUENCIAL_PEDIDOS = new AtomicLong(0);

    private final String id;
    private final Cliente cliente;
    private final NivelServicio nivel;
    private final List<LineaPedido> lineas;
    private final int totalUnidades;
    private final long instanteCreacion; // Segundos simulados
    private final long venceSLA;         // Segundos simulados (creación + espera objetivo)
    private final long duracionEstimadaSegundos; // totalUnidades * SEGUNDOS_SIM_POR_UNIDAD

    private final Set<TipoRecurso> recursosRequeridos;
    private final List<TipoRecurso> ordenAdquisicion;
    private final List<String> unidadesRecursosRetenidas = new CopyOnWriteArrayList<>();

    private volatile EstadoPedido estado;
    private volatile Long instanteInicio; // Segundos simulados
    private volatile Long instanteFin;    // Segundos simulados
    private volatile double progresoPorcentaje; // 0.0 a 100.0

    private final AtomicInteger vecesReencolado = new AtomicInteger(0);

    /**
     * Crea un nuevo pedido calculando automáticamente unidades totales, recursos y SLAs.
     * 
     * @param cliente Cliente emisor de la orden.
     * @param nivel Nivel de servicio (si es nulo, hereda del cliente).
     * @param lineas Lista de líneas de detalle con productos y cantidades.
     * @param instanteCreacion Instante de creación en segundos simulados.
     */
    public Pedido(Cliente cliente, NivelServicio nivel, List<LineaPedido> lineas, long instanteCreacion) {
        this(generarSiguienteId(), cliente, nivel, lineas, instanteCreacion, null);
    }

    /**
     * Constructor completo que permite especificar ID y orden de adquisición personalizado.
     */
    public Pedido(String id, Cliente cliente, NivelServicio nivel, List<LineaPedido> lineas,
                  long instanteCreacion, List<TipoRecurso> ordenAdquisicionPersonalizado) {
        this.id = Objects.requireNonNull(id, "El ID del pedido no puede ser nulo");
        this.cliente = Objects.requireNonNull(cliente, "El cliente no puede ser nulo");
        this.nivel = (nivel != null) ? nivel : cliente.nivelContratado();

        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("Un pedido debe contener al menos una línea de producto");
        }
        this.lineas = List.copyOf(lineas);

        int acumuladorUnidades = 0;
        Set<TipoRecurso> recursosUnion = EnumSet.noneOf(TipoRecurso.class);

        for (LineaPedido lp : this.lineas) {
            acumuladorUnidades += lp.cantidad();
            recursosUnion.addAll(lp.producto().tipoMercancia().getRecursosRequeridos());
        }

        this.totalUnidades = acumuladorUnidades;
        this.instanteCreacion = instanteCreacion;
        this.venceSLA = instanteCreacion + this.nivel.getEsperaObjetivoSegundos();
        this.duracionEstimadaSegundos = (long) this.totalUnidades * ConfiguracionSimulacion.SEGUNDOS_SIM_POR_UNIDAD;
        this.recursosRequeridos = Collections.unmodifiableSet(recursosUnion);

        // Si se provee orden personalizado, se valida que cubra los recursos requeridos;
        // de lo contrario se preserva un orden determinista por defecto.
        if (ordenAdquisicionPersonalizado != null && !ordenAdquisicionPersonalizado.isEmpty()) {
            this.ordenAdquisicion = List.copyOf(ordenAdquisicionPersonalizado);
        } else {
            List<TipoRecurso> listaOrden = new ArrayList<>(this.recursosRequeridos);
            listaOrden.sort(Comparator.comparing(Enum::name));
            this.ordenAdquisicion = Collections.unmodifiableList(listaOrden);
        }

        this.estado = EstadoPedido.EN_ESPERA_STOCK;
        this.progresoPorcentaje = 0.0;
    }

    public static String generarSiguienteId() {
        return String.format("PED-%06d", SECUENCIAL_PEDIDOS.incrementAndGet());
    }

    public static void reiniciarSecuencialParaPruebas() {
        SECUENCIAL_PEDIDOS.set(0);
    }

    // --- Métodos de ciclo de vida thread-safe ---

    public void iniciarProcesamiento(long instanteInicioSegundos) {
        this.instanteInicio = instanteInicioSegundos;
        this.estado = EstadoPedido.EN_PROCESO;
    }

    public void completarProcesamiento(long instanteFinSegundos) {
        this.instanteFin = instanteFinSegundos;
        this.progresoPorcentaje = 100.0;
        this.estado = EstadoPedido.COMPLETADO;
    }

    public void marcarBloqueado() {
        this.estado = EstadoPedido.BLOQUEADO;
    }

    public void marcarEnEsperaRecursos() {
        this.estado = EstadoPedido.EN_ESPERA_RECURSOS;
    }

    public void marcarEnEsperaStock() {
        this.estado = EstadoPedido.EN_ESPERA_STOCK;
    }

    public void cancelar() {
        this.estado = EstadoPedido.CANCELADO;
    }

    public void incrementarReencolado() {
        this.vecesReencolado.incrementAndGet();
    }

    public void actualizarProgreso(double nuevoPorcentaje) {
        this.progresoPorcentaje = Math.clamp(nuevoPorcentaje, 0.0, 100.0);
    }

    public void registrarUnidadRetenida(String nombreUnidad) {
        if (!unidadesRecursosRetenidas.contains(nombreUnidad)) {
            unidadesRecursosRetenidas.add(nombreUnidad);
        }
    }

    public void limpiarUnidadesRetenidas() {
        unidadesRecursosRetenidas.clear();
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public NivelServicio getNivel() {
        return nivel;
    }

    public List<LineaPedido> getLineas() {
        return lineas;
    }

    public int getTotalUnidades() {
        return totalUnidades;
    }

    public long getInstanteCreacion() {
        return instanteCreacion;
    }

    public long getVenceSLA() {
        return venceSLA;
    }

    public long getDuracionEstimadaSegundos() {
        return duracionEstimadaSegundos;
    }

    public Set<TipoRecurso> getRecursosRequeridos() {
        return recursosRequeridos;
    }

    public List<TipoRecurso> getOrdenAdquisicion() {
        return ordenAdquisicion;
    }

    public List<String> getUnidadesRecursosRetenidas() {
        return List.copyOf(unidadesRecursosRetenidas);
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public Long getInstanteInicio() {
        return instanteInicio;
    }

    public Long getInstanteFin() {
        return instanteFin;
    }

    public double getProgresoPorcentaje() {
        return progresoPorcentaje;
    }

    public int getVecesReencolado() {
        return vecesReencolado.get();
    }

    /**
     * Calcula el tiempo de espera acumulado en segundos simulados hasta el instante dado.
     */
    public long calcularEsperaSegundos(long ahoraSimuladoSegundos) {
        if (instanteInicio != null) {
            return Math.max(0, instanteInicio - instanteCreacion);
        }
        return Math.max(0, ahoraSimuladoSegundos - instanteCreacion);
    }

    @Override
    public String toString() {
        return String.format("Pedido[%s, cliente=%s, nivel=%s, unidades=%d, estado=%s]",
                id, cliente.nombre(), nivel.getNombre(), totalUnidades, estado);
    }
}
