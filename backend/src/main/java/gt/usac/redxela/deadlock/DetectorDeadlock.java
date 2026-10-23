package gt.usac.redxela.deadlock;

import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.modelo.Pedido;
import gt.usac.redxela.modelo.TipoRecurso;
import gt.usac.redxela.planificacion.Despachador;
import gt.usac.redxela.recursos.GestorRecursos;
import gt.usac.redxela.recursos.PoolRecurso;
import gt.usac.redxela.recursos.UnidadRecurso;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detector de Interbloqueos periódico basado en el Algoritmo Clásico de Detección
 * con Recursos Multi-Instancia (Silberschatz / Coffman / Holt / R14).
 * 
 * ¿Cómo funciona el algoritmo de matrices?
 * 1. Vector Disponible V (m recursos): Unidades libres de cada pool.
 * 2. Matriz Asignación A (n procesos x m recursos): Unidades retenidas por cada proceso.
 * 3. Matriz Solicitud S (n procesos x m recursos): Unidades que cada proceso espera activamente.
 * 4. Vector Trabajo W = V.
 * 5. Vector Fin[n]: Fin[i] = true si A[i] == 0 (no retiene recursos); sino false.
 * 6. Bucle de marcado:
 *    Buscar proceso i con Fin[i] == false y S[i] <= W.
 *    Si existe: W = W + A[i], Fin[i] = true, reiniciar búsqueda.
 * 7. Si al terminar quedan procesos con Fin[i] == false, dichos procesos están
 *    en INTERBLOQUEO (Deadlock), existiendo una cadena de espera circular irresoluble.
 */
@Component
public class DetectorDeadlock implements Runnable {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private gt.usac.redxela.eventos.RegistroEventos eventos;

    private void registrarEvento(String tipo, String mensaje) {
        if (eventos != null) eventos.registrar(tipo, mensaje);
    }


    private final Despachador despachador;
    private final GestorRecursos gestorRecursos;
    private final SimClock simClock;

    private final AtomicBoolean ejecutando = new AtomicBoolean(false);
    private volatile InformeConflicto ultimoInforme = InformeConflicto.sinConflicto();

    private Thread hiloDetector;

    public DetectorDeadlock(Despachador despachador, GestorRecursos gestorRecursos, SimClock simClock) {
        this.despachador = despachador;
        this.gestorRecursos = gestorRecursos;
        this.simClock = simClock;
    }

    public synchronized void iniciar() {
        if (!ejecutando.get()) {
            ejecutando.set(true);
            hiloDetector = new Thread(this, "hilo-detector-deadlock");
            hiloDetector.setDaemon(true);
            hiloDetector.start();
        }
    }

    public synchronized void detener() {
        ejecutando.set(false);
        if (hiloDetector != null) {
            hiloDetector.interrupt();
        }
    }

    @Override
    public void run() {
        while (ejecutando.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(ConfiguracionSimulacion.INTERVALO_DETECCION_DEADLOCK_MS);
                ejecutarDeteccion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Fallo en hilo detector de deadlock", e);
            }
        }
    }

    /**
     * Ejecuta el análisis matricial de interbloqueo y genera el InformeConflicto.
     */
    public synchronized InformeConflicto ejecutarDeteccion() {
        Collection<Pedido> activos = despachador.getPedidosEnProceso().values();
        List<Pedido> procesos = new ArrayList<>(activos);
        int n = procesos.size();

        if (n < 2) {
            this.ultimoInforme = InformeConflicto.sinConflicto();
            return this.ultimoInforme;
        }

        TipoRecurso[] tipos = TipoRecurso.values();
        int m = tipos.length;

        // 1. Vector Disponible V (m)
        int[] disponible = new int[m];
        for (int j = 0; j < m; j++) {
            disponible[j] = gestorRecursos.getPool(tipos[j]).getPermisosDisponibles();
        }

        // 2. Matriz Asignación A (n x m)
        int[][] asignacion = new int[n][m];
        for (int i = 0; i < n; i++) {
            Pedido p = procesos.get(i);
            for (int j = 0; j < m; j++) {
                PoolRecurso pool = gestorRecursos.getPool(tipos[j]);
                for (UnidadRecurso u : pool.getUnidades()) {
                    if (p.getId().equals(u.getDuenoActual())) {
                        asignacion[i][j]++;
                    }
                }
            }
        }

        // 3. Matriz Solicitud S (n x m)
        int[][] solicitud = new int[n][m];
        for (int i = 0; i < n; i++) {
            Pedido p = procesos.get(i);
            TipoRecurso req = p.getRecursoSolicitadoActual();
            if (req != null) {
                for (int j = 0; j < m; j++) {
                    if (tipos[j] == req) {
                        solicitud[i][j] = 1;
                        break;
                    }
                }
            }
        }

        // 4. Vector Trabajo W y Vector Fin
        int[] trabajo = disponible.clone();
        boolean[] fin = new boolean[n];

        for (int i = 0; i < n; i++) {
            boolean retieneAlgo = false;
            for (int j = 0; j < m; j++) {
                if (asignacion[i][j] > 0) {
                    retieneAlgo = true;
                    break;
                }
            }
            fin[i] = !retieneAlgo; // Si no retiene nada, no participa en ciclo
        }

        // 5. Bucle de marcado
        boolean cambio;
        do {
            cambio = false;
            for (int i = 0; i < n; i++) {
                if (!fin[i]) {
                    boolean puedeTerminar = true;
                    for (int j = 0; j < m; j++) {
                        if (solicitud[i][j] > trabajo[j]) {
                            puedeTerminar = false;
                            break;
                        }
                    }

                    if (puedeTerminar) {
                        for (int j = 0; j < m; j++) {
                            trabajo[j] += asignacion[i][j];
                        }
                        fin[i] = true;
                        cambio = true;
                    }
                }
            }
        } while (cambio);

        // 6. Extracción de procesos en interbloqueo
        List<Pedido> pedidosBloqueados = new ArrayList<>();
        Set<String> recursosDisputadosSet = new LinkedHashSet<>();
        List<AristaAsignacion> grafo = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            // Un proceso está en deadlock si fin[i] == false y tiene una solicitud pendiente
            TipoRecurso req = procesos.get(i).getRecursoSolicitadoActual();
            if (!fin[i] && req != null) {
                Pedido p = procesos.get(i);
                pedidosBloqueados.add(p);
                p.marcarBloqueado();

                // Aristas de Asignación (Recurso -> Proceso)
                for (String uNombre : p.getUnidadesRecursosRetenidas()) {
                    grafo.add(new AristaAsignacion(p.getId(), uNombre, AristaAsignacion.TipoArista.RETIENE));
                }

                // Arista de Solicitud (Proceso -> Recurso)
                grafo.add(new AristaAsignacion(p.getId(), req.getNombreLegible(), AristaAsignacion.TipoArista.SOLICITUD));
                recursosDisputadosSet.add(req.getNombreLegible());
            }
        }

        if (!pedidosBloqueados.isEmpty()) {
            if (!ultimoInforme.hayConflicto()) registrarEvento("DEADLOCK", "Conflicto detectado: " + pedidosBloqueados.stream().map(Pedido::getId).toList());
            StringBuilder explicacion = new StringBuilder();
            explicacion.append("¡INTERBLOQUEO DETECTADO (DEADLOCK)! ")
                    .append(pedidosBloqueados.size())
                    .append(" operaciones bloqueadas entre sí en espera circular. ");

            for (Pedido pb : pedidosBloqueados) {
                var solicitado = pb.getRecursoSolicitadoActual();
                explicacion.append(String.format(
                        "[%s retiene %s y espera %s] ",
                        pb.getId(),
                        pb.getUnidadesRecursosRetenidas(),
                        solicitado != null ? solicitado.getNombreLegible() : "ninguno"
                ));
            }
            explicacion.append("Condiciones de Coffman presentes: Exclusión mutua, Retención y espera, No expropiación y Espera circular.");

            List<String> idsInvolucrados = pedidosBloqueados.stream().map(Pedido::getId).toList();

            this.ultimoInforme = new InformeConflicto(
                    true,
                    idsInvolucrados,
                    new ArrayList<>(recursosDisputadosSet),
                    grafo,
                    explicacion.toString(),
                    simClock.ahoraSimuladoSegundos()
            );
        } else {
            this.ultimoInforme = InformeConflicto.sinConflicto();
        }

        return this.ultimoInforme;
    }

    public InformeConflicto getUltimoInforme() {
        return ultimoInforme;
    }
}
