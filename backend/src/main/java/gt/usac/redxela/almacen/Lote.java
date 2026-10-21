package gt.usac.redxela.almacen;

import gt.usac.redxela.modelo.Producto;

import java.util.*;

/**
 * Representa un lote de mercancía recibido y almacenado en REDXela (R13).
 * 
 * Analogía con Sistemas Operativos:
 * Un Lote equivale a un ARCHIVO en un sistema de archivos con ASIGNACIÓN INDEXADA
 * (análogo a la tabla de inodos en sistemas tipo UNIX).
 * 
 * En lugar de exigir ubicaciones contiguas contiguas en el mismo pasillo
 * (lo cual provocaría severa fragmentación externa), el lote mantiene una
 * TABLA DE ASIGNACIÓN INDEXADA (lista de EntradaAsignacion) apuntando a múltiples
 * ubicaciones físicas dispersas en cualquier pasillo o nivel.
 */
public class Lote {

    private final String id;
    private final Producto producto;
    private final int cantidadTotal;
    private final long instanteIngreso; // Segundos simulados
    private final List<EntradaAsignacion> tablaAsignacion;

    public Lote(String id, Producto producto, int cantidadTotal, long instanteIngreso, List<EntradaAsignacion> tablaAsignacion) {
        this.id = Objects.requireNonNull(id, "El ID de lote no puede ser nulo");
        this.producto = Objects.requireNonNull(producto, "El producto no puede ser nulo");
        this.cantidadTotal = cantidadTotal;
        this.instanteIngreso = instanteIngreso;
        this.tablaAsignacion = List.copyOf(tablaAsignacion);
    }

    public String getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public int getCantidadTotal() {
        return cantidadTotal;
    }

    public long getInstanteIngreso() {
        return instanteIngreso;
    }

    public List<EntradaAsignacion> getTablaAsignacion() {
        return tablaAsignacion;
    }

    public List<Ubicacion> getUbicaciones() {
        return tablaAsignacion.stream().map(EntradaAsignacion::ubicacion).toList();
    }

    /**
     * Calcula la fragmentación interna total generada por este lote.
     * Corresponde a la capacidad no aprovechada en la última celda del lote.
     */
    public int calcularFragmentacionInterna() {
        // Una ubicación liberada puede reasignarse: sólo cuentan las que aún pertenecen al lote.
        return tablaAsignacion.stream().map(e -> e.ubicacion().snapshot())
                .filter(u -> id.equals(u.loteId()))
                .mapToInt(u -> u.capacidadMaxima() - u.cantidadOcupada()).sum();
    }

    public LoteSnapshot snapshot() {
        List<LoteSnapshot.EntradaSnapshot> entradasSnap = tablaAsignacion.stream()
                .map(e -> e.ubicacion().snapshot())
                .filter(u -> id.equals(u.loteId()))
                .map(u -> new LoteSnapshot.EntradaSnapshot(u.id(), u.cantidadOcupada()))
                .toList();

        return new LoteSnapshot(
                id,
                producto.id(),
                producto.nombre(),
                entradasSnap.stream().mapToInt(LoteSnapshot.EntradaSnapshot::unidades).sum(),
                instanteIngreso,
                entradasSnap,
                calcularFragmentacionInterna()
        );
    }

    public record LoteSnapshot(
            String id,
            String productoId,
            String productoNombre,
            int cantidadTotal,
            long instanteIngreso,
            List<EntradaSnapshot> tablaAsignacion,
            int fragmentacionInternaUnidades
    ) {
        public record EntradaSnapshot(String ubicacionId, int unidades) {}
    }
}
