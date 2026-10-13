package gt.usac.redxela.planificacion;

import gt.usac.redxela.modelo.EstadoPedido;
import gt.usac.redxela.modelo.Pedido;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cola de procesos listos / en espera de asignación (Ready / Wait Queue).
 * 
 * Sincronizada mediante un ReentrantLock para permitir consultas concurrentes
 * desde el hilo Despachador y la interfaz de usuario.
 * 
 * Provee la lista de pedidos ordenada dinámicamente según la PolíticaAtencion
 * actualmente configurada.
 */
@Component
public class ListaEspera {

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Pedido> pedidos = new ArrayList<>();

    /**
     * Encola un nuevo pedido en la lista de espera.
     */
    public void agregar(Pedido pedido) {
        lock.lock();
        try {
            if (!pedidos.contains(pedido)) {
                pedidos.add(pedido);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remueve un pedido de la lista de espera (por haber sido despachado o cancelado).
     */
    public boolean remover(Pedido pedido) {
        lock.lock();
        try {
            return pedidos.remove(pedido);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retorna una copia de los pedidos ordenada según el criterio de la política activa.
     */
    public List<Pedido> obtenerCandidatosOrdenados(PoliticaAtencion politica, long ahoraSimulado) {
        lock.lock();
        try {
            List<Pedido> copia = new ArrayList<>(pedidos);
            copia.sort(politica.comparador(ahoraSimulado));
            return copia;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return pedidos.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean contiene(String pedidoId) {
        lock.lock();
        try {
            return pedidos.stream().anyMatch(p -> p.getId().equals(pedidoId));
        } finally {
            lock.unlock();
        }
    }

    public Optional<Pedido> buscarPorId(String pedidoId) {
        lock.lock();
        try {
            return pedidos.stream().filter(p -> p.getId().equals(pedidoId)).findFirst();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Genera una instantánea (snapshot) de la lista de espera para el reporte de telemetría.
     */
    public List<PedidoSnapshot> snapshot(PoliticaAtencion politica, long ahoraSimulado) {
        lock.lock();
        try {
            List<Pedido> copia = new ArrayList<>(pedidos);
            copia.sort(politica.comparador(ahoraSimulado));

            List<PedidoSnapshot> snapshots = new ArrayList<>(copia.size());
            int posicion = 1;
            for (Pedido p : copia) {
                long esperaSegundos = p.calcularEsperaSegundos(ahoraSimulado);
                long tiempoRestanteSLA = p.getVenceSLA() - ahoraSimulado;
                snapshots.add(new PedidoSnapshot(
                        posicion++,
                        p.getId(),
                        p.getCliente().nombre(),
                        p.getNivel().getNivel(),
                        p.getNivel().getNombre(),
                        p.getTotalUnidades(),
                        p.getEstado(),
                        p.getInstanteCreacion(),
                        esperaSegundos,
                        p.getNivel().getEsperaObjetivoSegundos(),
                        tiempoRestanteSLA,
                        p.getVecesReencolado(),
                        p.getRecursosRequeridos().stream().map(Enum::name).toList()
                ));
            }
            return Collections.unmodifiableList(snapshots);
        } finally {
            lock.unlock();
        }
    }

    public record PedidoSnapshot(
            int posicion,
            String id,
            String clienteNombre,
            int nivelNumero,
            String nivelNombre,
            int totalUnidades,
            EstadoPedido estado,
            long instanteCreacion,
            long esperaActualSegundos,
            long esperaObjetivoSegundos,
            long tiempoRestanteSLASegundos,
            int vecesReencolado,
            List<String> recursosRequeridos
    ) {}
}
