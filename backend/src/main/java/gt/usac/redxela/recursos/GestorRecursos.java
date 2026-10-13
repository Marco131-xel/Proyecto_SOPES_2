package gt.usac.redxela.recursos;

import gt.usac.redxela.modelo.Pedido;
import gt.usac.redxela.modelo.TipoRecurso;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestor central de recursos compartidos del centro de distribución REDXela.
 * 
 * Implementa dos estrategias fundamentales de asignación de recursos:
 * 
 * 1. MODO CON PREVENCIÓN (por defecto ON - Todo-o-nada atómico):
 *    Utiliza un ReentrantLock para serializar la solicitud de recursos. Si un proceso
 *    no puede obtener la totalidad de los recursos solicitados, realiza ROLLBACK
 *    inmediato devolviendo los que ya había obtenido.
 *    Fundamento teórico: Niega la condición de Coffman de 'Retención y Espera' (Hold and Wait).
 *    El interbloqueo es matemáticamente imposible en este modo.
 * 
 * 2. MODO SIN PREVENCIÓN (OFF para demostración de interbloqueos):
 *    Los procesos adquieren sus recursos de forma incremental uno a uno reteniendo
 *    los ya adquiridos mientras se bloquean en el semáforo esperando el siguiente.
 *    Cumple las 4 condiciones de Coffman, permitiendo el surgimiento de interbloqueos reales.
 */
@Component
public class GestorRecursos {

    private final Map<TipoRecurso, PoolRecurso> pools = new EnumMap<>(TipoRecurso.class);
    private final ReentrantLock lockAtencionAtomica = new ReentrantLock(true);
    private volatile boolean prevencionDeadlock = true;

    public GestorRecursos() {
        for (TipoRecurso tipo : TipoRecurso.values()) {
            pools.put(tipo, new PoolRecurso(tipo, tipo.getCapacidadDefecto()));
        }
    }

    /**
     * Intenta adquirir la totalidad de los recursos de un pedido de forma atómica.
     * En caso de que algún recurso no esté disponible, se revierten inmediatamente
     * todos los recursos adquiridos parcialmente (Rollback atómico).
     * 
     * @param pedido Pedido solicitante.
     * @param instanteSimulado Instante actual en segundos simulados.
     * @return Optional con la Concesion obtenida, o Optional.empty() si falló algún recurso.
     */
    public Optional<Concesion> intentarAdquirirTodoONada(Pedido pedido, long instanteSimulado) {
        lockAtencionAtomica.lock();
        try {
            Set<TipoRecurso> requeridos = pedido.getRecursosRequeridos();
            List<Concesion.Asignacion> obtenidas = new ArrayList<>(requeridos.size());
            boolean exitoTotal = true;

            for (TipoRecurso tipo : requeridos) {
                PoolRecurso pool = pools.get(tipo);
                Optional<UnidadRecurso> unidad = pool.tryAcquire(pedido.getId(), instanteSimulado);
                if (unidad.isPresent()) {
                    obtenidas.add(new Concesion.Asignacion(pool, unidad.get()));
                } else {
                    exitoTotal = false;
                    break;
                }
            }

            if (exitoTotal) {
                Concesion concesion = new Concesion(pedido.getId());
                pedido.limpiarUnidadesRetenidas();
                for (Concesion.Asignacion a : obtenidas) {
                    concesion.agregar(a.pool(), a.unidad());
                    pedido.registrarUnidadRetenida(a.unidad().getNombreLegible());
                }
                return Optional.of(concesion);
            } else {
                // Rollback atómico: devolvemos los permisos y unidades tomadas
                for (Concesion.Asignacion a : obtenidas) {
                    a.pool().release(a.unidad(), pedido.getId());
                }
                return Optional.empty();
            }
        } finally {
            lockAtencionAtomica.unlock();
        }
    }

    /**
     * Adquiere un recurso específico de forma individual (bloqueante e interrumpible).
     * Utilizado en modo SIN PREVENCIÓN por el trabajador para retener recursos paso a paso.
     * 
     * @param tipo Tipo de recurso solicitado.
     * @param pedidoId ID del pedido.
     * @param instanteSimulado Instante simulado.
     * @return Unidad asignada.
     * @throws InterruptedException si el hilo es interrumpido mientras espera.
     */
    public UnidadRecurso adquirirUnoAUno(TipoRecurso tipo, String pedidoId, long instanteSimulado) throws InterruptedException {
        PoolRecurso pool = pools.get(tipo);
        return pool.acquire(pedidoId, instanteSimulado);
    }

    /**
     * Libera una asignación individual hacia su pool.
     */
    public void liberarIndividual(TipoRecurso tipo, UnidadRecurso unidad, String pedidoId) {
        PoolRecurso pool = pools.get(tipo);
        if (pool != null && unidad != null) {
            pool.release(unidad, pedidoId);
        }
    }

    public PoolRecurso getPool(TipoRecurso tipo) {
        return pools.get(tipo);
    }

    public boolean isPrevencionDeadlock() {
        return prevencionDeadlock;
    }

    public void setPrevencionDeadlock(boolean prevencionDeadlock) {
        this.prevencionDeadlock = prevencionDeadlock;
    }

    /**
     * Genera una instantánea (snapshot) consistente de todos los pools de recursos.
     */
    public Map<TipoRecurso, PoolRecurso.PoolSnapshot> snapshot() {
        Map<TipoRecurso, PoolRecurso.PoolSnapshot> mapa = new EnumMap<>(TipoRecurso.class);
        for (Map.Entry<TipoRecurso, PoolRecurso> entry : pools.entrySet()) {
            mapa.put(entry.getKey(), entry.getValue().snapshot());
        }
        return Collections.unmodifiableMap(mapa);
    }
}
