package gt.usac.redxela.deadlock;

import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.Despachador;
import gt.usac.redxela.planificacion.ListaEspera;
import gt.usac.redxela.recursos.GestorRecursos;
import gt.usac.redxela.recursos.PoolRecurso;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Orquestador del Escenario de Demostración y Resolución Manual de Interbloqueos (R14, R15).
 * 
 * Permite reproducir de manera determinista al 100% un interbloqueo real:
 * 1. Desactiva la prevención atómica Todo-o-Nada.
 * 2. Reduce temporalmente los recursos MONTACARGAS y CARGA a 1 unidad disponible
 *    mediante modo MANTENIMIENTO en las unidades sobrantes (MTC-02, MTC-03 y CRG-02).
 * 3. Inyecta dos pedidos con órdenes cruzados de adquisición:
 *    - Pedido A (Pesado): [MONTACARGAS, CARGA, ENCARGADO]
 *    - Pedido B (Voluminoso): [CARGA, MONTACARGAS, EMPAQUE]
 * 
 * 4. Resolución Manual (R15):
 *    El operador selecciona cuál proceso continúa. El proceso perdedor (víctima)
 *    es interrumpido (Thread.interrupt), ejecuta el rollback limpio de lo retenido
 *    y vuelve a la lista de espera, permitiendo al ganador finalizar exitosamente.
 */
@Component
public class GestorEscenarioDeadlock {

    private final GestorRecursos gestorRecursos;
    private final Despachador despachador;
    private final DetectorDeadlock detectorDeadlock;
    private final ListaEspera listaEspera;
    private final GestorClientes gestorClientes;
    private final SimClock simClock;

    public GestorEscenarioDeadlock(GestorRecursos gestorRecursos, Despachador despachador,
                                   DetectorDeadlock detectorDeadlock, ListaEspera listaEspera,
                                   GestorClientes gestorClientes, SimClock simClock) {
        this.gestorRecursos = gestorRecursos;
        this.despachador = despachador;
        this.detectorDeadlock = detectorDeadlock;
        this.listaEspera = listaEspera;
        this.gestorClientes = gestorClientes;
        this.simClock = simClock;
    }

    /**
     * Provoca deliberadamente el escenario de interbloqueo reproducible (R14).
     */
    public synchronized List<Pedido> provocarEscenarioDeadlock() {
        // 1. Desactivar prevención de deadlock
        gestorRecursos.setPrevencionDeadlock(false);

        // 2. Aislar unidades a 1 Montacargas y 1 Carga mediante Mantenimiento
        PoolRecurso poolMontacargas = gestorRecursos.getPool(TipoRecurso.MONTACARGAS);
        PoolRecurso poolCarga = gestorRecursos.getPool(TipoRecurso.CARGA);

        poolMontacargas.ponerEnMantenimiento("MTC-02");
        poolMontacargas.ponerEnMantenimiento("MTC-03");
        poolCarga.ponerEnMantenimiento("CRG-02");

        Cliente cliente = gestorClientes.obtenerTodos().get(0);
        Producto prodPesado = CatalogoProductos.obtenerPorId("PROD-PES-01");
        Producto prodVoluminoso = CatalogoProductos.obtenerPorId("PROD-VOL-01");

        long ahoraSim = simClock.ahoraSimuladoSegundos();

        // 3. Pedido A: Pesado con orden [MONTACARGAS, CARGA, ENCARGADO]
        Pedido pedidoA = new Pedido(
                Pedido.generarSiguienteId(),
                cliente,
                NivelServicio.EXPRES,
                List.of(new LineaPedido(prodPesado, 2)),
                ahoraSim,
                List.of(TipoRecurso.MONTACARGAS, TipoRecurso.CARGA, TipoRecurso.ENCARGADO)
        );

        // 4. Pedido B: Voluminoso con orden cruzado [CARGA, MONTACARGAS, EMPAQUE]
        Pedido pedidoB = new Pedido(
                Pedido.generarSiguienteId(),
                cliente,
                NivelServicio.EXPRES,
                List.of(new LineaPedido(prodVoluminoso, 1)),
                ahoraSim,
                List.of(TipoRecurso.CARGA, TipoRecurso.MONTACARGAS, TipoRecurso.EMPAQUE)
        );

        despachador.registrarPedido(pedidoA);
        despachador.registrarPedido(pedidoB);

        return List.of(pedidoA, pedidoB);
    }

    /**
     * Resuelve manualmente el conflicto según la decisión del usuario (R15).
     * 
     * @param pedidoGanadorId ID del pedido elegido para continuar.
     */
    public synchronized void resolverConflictoManual(String pedidoGanadorId) {
        InformeConflicto informe = detectorDeadlock.ejecutarDeteccion();
        if (!informe.hayConflicto()) {
            throw new IllegalStateException("No existe ningún interbloqueo activo para resolver");
        }

        if (!informe.pedidosInvolucrados().contains(pedidoGanadorId)) {
            throw new IllegalArgumentException("El pedido ganador no forma parte de este conflicto: " + pedidoGanadorId);
        }

        // Para cada pedido en conflicto que NO sea el ganador (víctimas elegidas para retroceso)
        for (String pedidoId : informe.pedidosInvolucrados()) {
            if (!pedidoId.equals(pedidoGanadorId)) {
                Thread hiloVictima = despachador.getHiloTrabajador(pedidoId);
                if (hiloVictima != null && hiloVictima.isAlive()) {
                    // Expropiación mediante interrupción controlada: el catch(InterruptedException)
                    // del trabajador ejecutará el rollback y devolverá el proceso a la lista de espera
                    hiloVictima.interrupt();
                }
            }
        }

        // Despertar despachador para re-evaluar
        despachador.despertar();
    }

    /**
     * Restaura las unidades puestas en mantenimiento y reactiva la prevención.
     */
    public synchronized void restaurarMantenimientoYPrevencion() {
        PoolRecurso poolMontacargas = gestorRecursos.getPool(TipoRecurso.MONTACARGAS);
        PoolRecurso poolCarga = gestorRecursos.getPool(TipoRecurso.CARGA);

        poolMontacargas.quitarMantenimiento("MTC-02");
        poolMontacargas.quitarMantenimiento("MTC-03");
        poolCarga.quitarMantenimiento("CRG-02");

        gestorRecursos.setPrevencionDeadlock(true);
    }
}
