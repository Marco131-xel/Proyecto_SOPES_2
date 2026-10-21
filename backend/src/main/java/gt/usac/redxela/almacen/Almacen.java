package gt.usac.redxela.almacen;

import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.modelo.Producto;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Administrador de Almacenamiento Físico del Almacén (Storage Management / R13).
 * 
 * Modela la administración de memoria y disco de un Sistema Operativo:
 * 1. MAPA DE BITS (Free-Space Bitmap):
 *    Un BitSet de 240 bits donde el bit i representa el estado de la celda i
 *    (1 = Libre, 0 = Ocupado). Búsqueda eficiente por 'Primer Ajuste' (First Fit).
 * 
 * 2. ASIGNACIÓN NO CONTIGUA E INDEXADA (Indexed Non-Contiguous Allocation):
 *    Un lote que requiere N celdas se distribuye en las primeras celdas libres encontradas,
 *    sin exigir contigüidad física. Esto ELIMINA la fragmentación externa (External Fragmentation),
 *    permitiendo aprovechar el 100% del espacio libre disponible.
 * 
 * 3. CONTROL DE CONCURRENCIA:
 *    Protegido con un ReentrantReadWriteLock justo: permite múltiples lecturas concurrentes
 *    para telemetría y consultas de la UI web mientras garantiza exclusión mutua estricta
 *    durante la asignación y liberación física de celdas.
 */
@Component
public class Almacen {

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private final BitSet mapaBitsLibres = new BitSet(ConfiguracionSimulacion.ALMACEN_TOTAL_UBICACIONES);
    private final Ubicacion[] ubicaciones = new Ubicacion[ConfiguracionSimulacion.ALMACEN_TOTAL_UBICACIONES];
    private final Map<String, Lote> lotes = new ConcurrentHashMap<>();

    public Almacen() {
        int indice = 0;
        for (int p = 1; p <= ConfiguracionSimulacion.ALMACEN_PASILLOS; p++) {
            for (int n = 1; n <= ConfiguracionSimulacion.ALMACEN_NIVELES; n++) {
                for (int pos = 1; pos <= ConfiguracionSimulacion.ALMACEN_POSICIONES; pos++) {
                    ubicaciones[indice] = new Ubicacion(p, n, pos, indice);
                    mapaBitsLibres.set(indice); // Inicialmente todas las celdas están libres (bit = 1)
                    indice++;
                }
            }
        }
    }

    /**
     * Intenta asignar celdas físicas para un nuevo lote utilizando asignación no contigua (R13).
     * 
     * @param loteId ID único del lote.
     * @param producto Producto a almacenar.
     * @param cantidadTotal Unidades a almacenar.
     * @param instanteSimulado Instante de ingreso en segundos simulados.
     * @return Optional con el Lote asignado, o Optional.empty() si el almacén no tiene suficientes celdas.
     */
    public Optional<Lote> intentarAsignarLote(String loteId, Producto producto, int cantidadTotal, long instanteSimulado) {
        if (cantidadTotal <= 0) {
            throw new IllegalArgumentException("La cantidad total del lote debe ser mayor a 0");
        }

        int unidadesPorCelda = producto.unidadesPorUbicacion();
        int celdasNecesarias = (int) Math.ceil((double) cantidadTotal / unidadesPorCelda);

        rwLock.writeLock().lock();
        try {
            // Verificamos si hay suficientes celdas libres en el mapa de bits
            if (mapaBitsLibres.cardinality() < celdasNecesarias) {
                return Optional.empty(); // Almacén lleno o espacio insuficiente
            }

            // Primer Ajuste (First-Fit) sobre el mapa de bits sin exigir contigüidad
            List<Integer> indicesAsignados = new ArrayList<>(celdasNecesarias);
            int bitBuscado = 0;
            while (indicesAsignados.size() < celdasNecesarias) {
                int libre = mapaBitsLibres.nextSetBit(bitBuscado);
                if (libre < 0 || libre >= ConfiguracionSimulacion.ALMACEN_TOTAL_UBICACIONES) {
                    break;
                }
                indicesAsignados.add(libre);
                bitBuscado = libre + 1;
            }

            if (indicesAsignados.size() < celdasNecesarias) {
                return Optional.empty();
            }

            // Asignación física y construcción de la tabla indexada del lote
            List<EntradaAsignacion> tabla = new ArrayList<>(celdasNecesarias);
            int unidadesPendientes = cantidadTotal;

            for (int idx : indicesAsignados) {
                mapaBitsLibres.clear(idx); // Marcamos la celda como ocupada (bit = 0)
                int unidadesEnCelda = Math.min(unidadesPendientes, unidadesPorCelda);

                Ubicacion u = ubicaciones[idx];
                u.asignar(producto, unidadesEnCelda, loteId);
                tabla.add(new EntradaAsignacion(u, unidadesEnCelda));

                unidadesPendientes -= unidadesEnCelda;
            }

            Lote nuevoLote = new Lote(loteId, producto, cantidadTotal, instanteSimulado, tabla);
            lotes.put(loteId, nuevoLote);
            return Optional.of(nuevoLote);

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Libera una ubicación física específica reintegrando su bit al mapa de bits libres.
     */
    public void liberarUbicacion(Ubicacion ubicacion) {
        if (ubicacion == null) return;

        rwLock.writeLock().lock();
        try {
            int idx = ubicacion.getIndiceLineal();
            String loteId = ubicacion.getLoteIdActual();
            ubicacion.liberar();
            Lote lote = loteId == null ? null : lotes.get(loteId);
            if (lote != null && lote.getUbicaciones().stream().noneMatch(u -> loteId.equals(u.getLoteIdActual()))) {
                lotes.remove(loteId);
            }
            mapaBitsLibres.set(idx); // Reintegramos el bit libre (bit = 1)
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Libera completamente un lote y todas las celdas de su tabla de asignación indexada.
     */
    public void liberarLoteCompleto(String loteId) {
        rwLock.writeLock().lock();
        try {
            Lote lote = lotes.remove(loteId);
            if (lote != null) {
                for (EntradaAsignacion entrada : lote.getTablaAsignacion()) {
                    if (loteId.equals(entrada.ubicacion().getLoteIdActual())) liberarUbicacion(entrada.ubicacion());
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public int getUbicacionesLibres() {
        rwLock.readLock().lock();
        try {
            return mapaBitsLibres.cardinality();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getUbicacionesOcupadas() {
        return ConfiguracionSimulacion.ALMACEN_TOTAL_UBICACIONES - getUbicacionesLibres();
    }

    public Optional<Lote> buscarLote(String loteId) {
        return Optional.ofNullable(lotes.get(loteId));
    }

    public List<Lote> getLotesActivos() {
        return new ArrayList<>(lotes.values());
    }

    public Ubicacion getUbicacionPorIndice(int indice) {
        return ubicaciones[indice];
    }

    /**
     * Construye un snapshot consistente del almacén bajo readLock.
     */
    public AlmacenSnapshot snapshot(int lotesPendientesEspacio) {
        rwLock.readLock().lock();
        try {
            int libres = mapaBitsLibres.cardinality();
            int ocupadas = ConfiguracionSimulacion.ALMACEN_TOTAL_UBICACIONES - libres;
            double pctOcupacion = ((double) ocupadas / ConfiguracionSimulacion.ALMACEN_TOTAL_UBICACIONES) * 100.0;

            List<Ubicacion.UbicacionSnapshot> uSnaps = new ArrayList<>(ubicaciones.length);
            for (Ubicacion u : ubicaciones) {
                uSnaps.add(u.snapshot());
            }

            List<Lote.LoteSnapshot> lSnaps = new ArrayList<>(lotes.size());
            int fragmentacionInternaTotal = 0;
            for (Lote l : lotes.values()) {
                lSnaps.add(l.snapshot());
                fragmentacionInternaTotal += l.calcularFragmentacionInterna();
            }

            return new AlmacenSnapshot(
                    ConfiguracionSimulacion.ALMACEN_TOTAL_UBICACIONES,
                    libres,
                    ocupadas,
                    pctOcupacion,
                    fragmentacionInternaTotal,
                    lotesPendientesEspacio,
                    uSnaps,
                    lSnaps
            );
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
