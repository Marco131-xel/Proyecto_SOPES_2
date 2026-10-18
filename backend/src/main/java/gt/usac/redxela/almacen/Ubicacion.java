package gt.usac.redxela.almacen;

import gt.usac.redxela.modelo.Producto;

import java.util.Objects;

/**
 * Representa una celda o ubicación física dentro del almacén de REDXela.
 * 
 * Dirección física tridimensional: Pasillo (1..6) - Nivel (1..4) - Posición (1..10).
 * Total: 240 ubicaciones.
 * 
 * Analogía con Sistemas Operativos:
 * Cada ubicación física equivale a un MARCO DE PÁGINA (Page Frame) en memoria principal
 * o a un BLOQUE FÍSICO en almacenamiento secundario.
 * Puede almacenar hasta 'capacidadMaxima' unidades de un único producto a la vez.
 */
public class Ubicacion {

    private final String id; // Formato: "P1-N2-05"
    private final int pasillo;
    private final int nivel;
    private final int posicion;
    private final int indiceLineal; // 0 a 239 para mapa de bits

    private Producto producto;
    private int cantidadOcupada;
    private int capacidadMaxima;
    private String loteIdActual;

    public Ubicacion(int pasillo, int nivel, int posicion, int indiceLineal) {
        this.pasillo = pasillo;
        this.nivel = nivel;
        this.posicion = posicion;
        this.indiceLineal = indiceLineal;
        this.id = String.format("P%d-N%d-%02d", pasillo, nivel, posicion);
        this.producto = null;
        this.cantidadOcupada = 0;
        this.capacidadMaxima = 0;
        this.loteIdActual = null;
    }

    public synchronized void asignar(Producto producto, int cantidad, String loteId) {
        this.producto = Objects.requireNonNull(producto, "El producto no puede ser nulo");
        this.capacidadMaxima = producto.unidadesPorUbicacion();
        if (cantidad <= 0 || cantidad > this.capacidadMaxima) {
            throw new IllegalArgumentException(String.format(
                    "Cantidad inválida (%d). Máximo por celda para %s es %d",
                    cantidad, producto.nombre(), this.capacidadMaxima));
        }
        this.cantidadOcupada = cantidad;
        this.loteIdActual = loteId;
    }

    public synchronized void liberar() {
        this.producto = null;
        this.cantidadOcupada = 0;
        this.capacidadMaxima = 0;
        this.loteIdActual = null;
    }

    public synchronized boolean isLibre() {
        return cantidadOcupada == 0 && producto == null;
    }

    public synchronized int getEspacioVacioInterno() {
        if (isLibre()) return 0;
        return capacidadMaxima - cantidadOcupada;
    }

    public String getId() {
        return id;
    }

    public int getPasillo() {
        return pasillo;
    }

    public int getNivel() {
        return nivel;
    }

    public int getPosicion() {
        return posicion;
    }

    public int getIndiceLineal() {
        return indiceLineal;
    }

    public synchronized Producto getProducto() {
        return producto;
    }

    public synchronized int getCantidadOcupada() {
        return cantidadOcupada;
    }

    public synchronized int getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public synchronized String getLoteIdActual() {
        return loteIdActual;
    }

    public synchronized UbicacionSnapshot snapshot() {
        return new UbicacionSnapshot(
                id, pasillo, nivel, posicion, indiceLineal,
                producto != null ? producto.id() : null,
                producto != null ? producto.nombre() : null,
                producto != null ? producto.tipoMercancia().name() : null,
                cantidadOcupada, capacidadMaxima, isLibre(), loteIdActual
        );
    }

    public record UbicacionSnapshot(
            String id,
            int pasillo,
            int nivel,
            int posicion,
            int indiceLineal,
            String productoId,
            String productoNombre,
            String tipoMercancia,
            int cantidadOcupada,
            int capacidadMaxima,
            boolean libre,
            String loteId
    ) {}
}
