package gt.usac.redxela.generador;

import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.Despachador;
import gt.usac.redxela.planificacion.ListaEspera;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generador automático de pedidos (R3).
 * 
 * Modela la llegada de transacciones al centro de distribución mediante un
 * proceso estocástico con tasa configurable (pedidos por minuto simulado).
 * Soporta inicialización con semilla configurable para garantizar repetibilidad
 * de su secuencia de productos. El comparador de políticas usa su carga aislada (R5).
 */
@Component
public class GeneradorPedidos implements Runnable {

    private final GestorClientes gestorClientes;
    private final Despachador despachador;
    private final SimClock simClock;

    private final AtomicBoolean ejecutando = new AtomicBoolean(false);
    private final AtomicBoolean pausado = new AtomicBoolean(false);

    private volatile double tasaPedidosPorMinutoSimulado = 6.0; // media de 6 llegadas/min
    private Long semillaConfigurada = null;
    private Random random = new Random();

    private volatile Thread hiloGenerador;

    public GeneradorPedidos(GestorClientes gestorClientes, ListaEspera listaEspera,
                            Despachador despachador, SimClock simClock) {
        this.gestorClientes = gestorClientes;
        this.despachador = despachador;
        this.simClock = simClock;
    }

    public synchronized void iniciar() {
        if (!ejecutando.get()) {
            ejecutando.set(true);
            pausado.set(false);
            if (semillaConfigurada != null) {
                this.random = new Random(semillaConfigurada);
            } else {
                this.random = new Random();
            }
            this.hiloGenerador = new Thread(this, "hilo-generador-pedidos");
            this.hiloGenerador.setDaemon(true);
            this.hiloGenerador.start();
        }
    }

    public synchronized void pausar() {
        pausado.set(true);
    }

    public synchronized void reanudar() {
        pausado.set(false);
        notifyAll();
    }

    public synchronized void detener() {
        ejecutando.set(false);
        notifyAll();
        if (hiloGenerador != null) {
            hiloGenerador.interrupt();
        }
    }

    /** Cada ciclo admite a lo sumo un pedido y luego espera un intervalo exponencial. */
    @Override
    public void run() {
        Thread actual = Thread.currentThread();
        while (ejecutando.get() && hiloGenerador == actual && !actual.isInterrupted()) {
            try {
                long intervaloMs;
                synchronized (this) {
                    if (!ejecutando.get() || hiloGenerador != actual) break;
                    if (pausado.get() || simClock.isPausado()) {
                        // wait libera el monitor: los controles HTTP pueden reanudar o detener.
                        wait(100);
                        continue;
                    }
                    despachador.registrarPedido(generarPedidoAleatorio());
                    intervaloMs = Math.max(1L, Math.round(-Math.log1p(-random.nextDouble()) * 60000 / tasaPedidosPorMinutoSimulado));
                }
                simClock.dormirSimuladoMs(intervaloMs);
            } catch (InterruptedException e) {
                actual.interrupt();
                break;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Fallo en generador de pedidos", e);
                // Un fallo persistente no debe convertirse en un bucle de errores sin espera.
                try { Thread.sleep(100); } catch (InterruptedException ie) { actual.interrupt(); break; }
            }
        }
    }

    /**
     * Construye un pedido aleatorio respetando la distribución de niveles de servicio
     * y líneas de detalle de productos del catálogo.
     */
    public synchronized Pedido generarPedidoAleatorio() {

        // Distribución ponderada aproximada de niveles:
        // Exprés (~5%), Prioritario (~15%), Estándar (~30%), Programado (~30%), Económico (~20%)
        int dado = random.nextInt(100);
        NivelServicio nivel;
        if (dado < 5) nivel = NivelServicio.EXPRES;
        else if (dado < 20) nivel = NivelServicio.PRIORITARIO;
        else if (dado < 50) nivel = NivelServicio.ESTANDAR;
        else if (dado < 80) nivel = NivelServicio.PROGRAMADO;
        else nivel = NivelServicio.ECONOMICO;

        List<Cliente> candidatos = gestorClientes.obtenerTodos().stream()
                .filter(c -> c.nivelContratado() == nivel).sorted(Comparator.comparing(Cliente::id)).toList();
        Cliente cliente = candidatos.get(random.nextInt(candidatos.size()));
        int numLineas = 1 + random.nextInt(4); // 1 a 4 líneas
        List<Producto> catalogo = CatalogoProductos.obtenerTodos();
        List<LineaPedido> lineas = new ArrayList<>();
        Set<String> productosUsados = new HashSet<>();

        for (int i = 0; i < numLineas; i++) {
            Producto p;
            do {
                p = catalogo.get(random.nextInt(catalogo.size()));
            } while (productosUsados.contains(p.id()));
            productosUsados.add(p.id());

            int cantidad = 1 + random.nextInt(40); // 1 a 40 unidades por línea
            lineas.add(new LineaPedido(p, cantidad));
        }

        long ahoraSim = simClock.ahoraSimuladoSegundos();
        return new Pedido(cliente, nivel, lineas, ahoraSim);
    }

    public void setTasaPedidosPorMinutoSimulado(double tasa) {
        if (!Double.isFinite(tasa) || tasa <= 0) throw new IllegalArgumentException("La tasa debe ser finita y positiva");
        this.tasaPedidosPorMinutoSimulado = tasa;
    }

    public double getTasaPedidosPorMinutoSimulado() {
        return tasaPedidosPorMinutoSimulado;
    }

    public synchronized void setSemilla(Long semilla) {
        this.semillaConfigurada = semilla;
        if (semilla != null) {
            this.random = new Random(semilla);
        }
    }

    public boolean isEjecutando() {
        return ejecutando.get();
    }

    public boolean isPausado() {
        return pausado.get();
    }
}
