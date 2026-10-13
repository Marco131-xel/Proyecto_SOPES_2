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
 * y reproducibilidad de escenarios de carga en el comparador de políticas (R5).
 */
@Component
public class GeneradorPedidos implements Runnable {

    private final GestorClientes gestorClientes;
    private final ListaEspera listaEspera;
    private final Despachador despachador;
    private final SimClock simClock;

    private final AtomicBoolean ejecutando = new AtomicBoolean(false);
    private final AtomicBoolean pausado = new AtomicBoolean(false);

    private double tasaPedidosPorMinutoSimulado = 6.0; // 1 pedido cada 10s simulados
    private Long semillaConfigurada = null;
    private Random random = new Random();

    private Thread hiloGenerador;

    public GeneradorPedidos(GestorClientes gestorClientes, ListaEspera listaEspera,
                            Despachador despachador, SimClock simClock) {
        this.gestorClientes = gestorClientes;
        this.listaEspera = listaEspera;
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
    }

    public synchronized void detener() {
        ejecutando.set(false);
        if (hiloGenerador != null) {
            hiloGenerador.interrupt();
        }
    }

    @Override
    public void run() {
        while (ejecutando.get() && !Thread.currentThread().isInterrupted()) {
            try {
                if (pausado.get()) {
                    Thread.sleep(200);
                    continue;
                }

                // Generar un pedido aleatorio realista
                Pedido pedido = generarPedidoAleatorio();
                listaEspera.agregar(pedido);
                despachador.despertar();

                // Calcular intervalo de llegada (en segundos simulados)
                double intervaloSimuladoSegundos = 60.0 / Math.max(0.1, tasaPedidosPorMinutoSimulado);
                simClock.dormirSimuladoSegundos((long) Math.max(1, intervaloSimuladoSegundos));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.printf("[ERROR] Fallo en generador de pedidos: %s%n", e.getMessage());
            }
        }
    }

    /**
     * Construye un pedido aleatorio respetando la distribución de niveles de servicio
     * y líneas de detalle de productos del catálogo.
     */
    public Pedido generarPedidoAleatorio() {
        Cliente cliente = gestorClientes.obtenerAleatorio(random);

        // Distribución ponderada aproximada de niveles:
        // Exprés (~5%), Prioritario (~15%), Estándar (~30%), Programado (~30%), Económico (~20%)
        int dado = random.nextInt(100);
        NivelServicio nivel;
        if (dado < 5) nivel = NivelServicio.EXPRES;
        else if (dado < 20) nivel = NivelServicio.PRIORITARIO;
        else if (dado < 50) nivel = NivelServicio.ESTANDAR;
        else if (dado < 80) nivel = NivelServicio.PROGRAMADO;
        else nivel = NivelServicio.ECONOMICO;

        int numLineas = 1 + random.nextInt(3); // 1 a 3 líneas
        List<Producto> catalogo = CatalogoProductos.obtenerTodos();
        List<LineaPedido> lineas = new ArrayList<>();
        Set<String> productosUsados = new HashSet<>();

        for (int i = 0; i < numLineas; i++) {
            Producto p;
            do {
                p = catalogo.get(random.nextInt(catalogo.size()));
            } while (productosUsados.contains(p.id()));
            productosUsados.add(p.id());

            int cantidad = 1 + random.nextInt(10); // 1 a 10 unidades por línea
            lineas.add(new LineaPedido(p, cantidad));
        }

        long ahoraSim = simClock.ahoraSimuladoSegundos();
        return new Pedido(cliente, nivel, lineas, ahoraSim);
    }

    public void setTasaPedidosPorMinutoSimulado(double tasa) {
        this.tasaPedidosPorMinutoSimulado = Math.max(0.1, tasa);
    }

    public double getTasaPedidosPorMinutoSimulado() {
        return tasaPedidosPorMinutoSimulado;
    }

    public void setSemilla(Long semilla) {
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
