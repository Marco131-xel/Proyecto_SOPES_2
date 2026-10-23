package gt.usac.redxela.comparacion;

import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Genera una carga reproducible y la ejecuta en cuatro motores nuevos, sin beans en vivo. */
public class SimuladorComparativo {
    public static final String ALCANCE = "Stock suficiente, prevención activa, despacho no expropiativo y recursos con capacidades reales. "
            + "Llegadas exponenciales cuantizadas a segundos; reloj lógico con hilos reales. Inanición: espera > 2 × SLA; para Exprés (SLA 0), toda espera positiva cuenta. "
            + "Ocupación media ponderada por las 26 unidades, desde la primera llegada hasta la última finalización.";

    /** Descriptor inmutable: crea un Pedido independiente para cada política. */
    public record PedidoCarga(String id, NivelServicio nivel, List<LineaPedido> lineas, long llegada) {
        public PedidoCarga { lineas = List.copyOf(lineas); }
        Pedido instanciar() {
            Cliente cliente = new Cliente("CMP-CLI-" + nivel.getNivel(), "Cliente comparativo " + nivel.getNombre(), "comparacion@redxela.gt", nivel);
            // ID local: nunca consumir ni reiniciar el secuencial del motor en vivo.
            return new Pedido(id, cliente, nivel, lineas, llegada, null);
        }
    }

    /** Semilla y catálogo ordenado fijan todas las llamadas aleatorias. */
    public List<PedidoCarga> generarCarga(ConfiguracionComparacion config) {
        Random random = new Random(config.semilla());
        List<Producto> catalogo = CatalogoProductos.obtenerTodos().stream().sorted(Comparator.comparing(Producto::id)).toList();
        List<PedidoCarga> carga = new ArrayList<>();
        double llegadaAcumulada = 0;
        for (int i = 0; i < config.cantidadPedidos(); i++) {
            if (i > 0) llegadaAcumulada += -Math.log1p(-random.nextDouble()) * 60 / config.tasaLlegadasPorMinuto();
            long llegada = (long) Math.floor(llegadaAcumulada);
            int dado = random.nextInt(100);
            int nivel = i < 5 ? i + 1 : dado < 5 ? 1 : dado < 20 ? 2 : dado < 50 ? 3 : dado < 80 ? 4 : 5;
            List<Producto> opciones = new ArrayList<>(catalogo);
            Collections.shuffle(opciones, random);
            int cantidadLineas = 1 + random.nextInt(4);
            List<LineaPedido> lineas = new ArrayList<>();
            for (int j = 0; j < cantidadLineas; j++) lineas.add(new LineaPedido(opciones.get(j), 1 + random.nextInt(40)));
            carga.add(new PedidoCarga(String.format(Locale.ROOT, "CMP-%06d", i + 1), NivelServicio.fromNivel(nivel), lineas, llegada));
        }
        return List.copyOf(carga);
    }

    /** Sólo cambia la política; cada corrida reconstruye todos sus objetos mutables. */
    public ResultadoComparacion ejecutar(ConfiguracionComparacion config) throws InterruptedException {
        List<PedidoCarga> carga = generarCarga(config);
        List<PoliticaAtencion> politicas = List.of(new PoliticaPrioridadEstricta(), new PoliticaPrioridadConAging(), new PoliticaMenorCantidadPrimero(), new PoliticaEDF());
        List<ResultadoComparacion.ResultadoPolitica> resultados = new ArrayList<>();
        for (PoliticaAtencion politica : politicas) resultados.add(new MotorComparativo(politica).ejecutar(carga));
        return new ResultadoComparacion(config, huella(carga), ALCANCE, resultados, concluir(resultados));
    }

    /** Huella de llegadas, niveles, productos y cantidades, sin fechas reales. */
    private String huella(List<PedidoCarga> carga) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (PedidoCarga p : carga) {
                String lineas = p.lineas().stream().map(l -> l.producto().id() + ":" + l.cantidad()).reduce("", (a, b) -> a + ";" + b);
                digest.update((p.id() + "|" + p.llegada() + "|" + p.nivel().name() + "|" + lineas + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 no disponible", e); }
    }

    /** Recomendación de esta carga: no extrapola una política universalmente óptima. */
    private String concluir(List<ResultadoComparacion.ResultadoPolitica> resultados) {
        var espera = resultados.stream().min(Comparator.comparingDouble(ResultadoComparacion.ResultadoPolitica::esperaPromedioSegundos)).orElseThrow();
        var sla = resultados.stream().min(Comparator.comparingDouble((ResultadoComparacion.ResultadoPolitica r) -> -r.porcentajeSLA())
                .thenComparingDouble(ResultadoComparacion.ResultadoPolitica::esperaPromedioSegundos)).orElseThrow();
        return String.format(Locale.ROOT, "En esta carga, %s obtuvo la menor espera media (%.1f s) y %s el mayor cumplimiento SLA (%.1f%%; desempate por espera media). "
                + "Para priorizar SLA, la recomendación de esta corrida es %s. Revise la espera por nivel y repita con otras semillas y tasas antes de generalizar. "
                + "Una carga finita mide esperas excesivas; no demuestra inanición indefinida.", espera.politica(), espera.esperaPromedioSegundos(), sla.politica(), sla.porcentajeSLA(), sla.politica());
    }
}
