package gt.usac.redxela.modelo;

import java.util.*;

/**
 * Catálogo central e inmutable de productos disponibles en REDXela (R11).
 * 
 * Contiene 18 productos predefinidos (3 por cada uno de los 6 tipos de mercancía),
 * con factores de empaque diferenciados por volumen y peso.
 */
public final class CatalogoProductos {

    private static final Map<String, Producto> PRODUCTOS_POR_ID;
    private static final List<Producto> TODOS_LOS_PRODUCTOS;

    static {
        List<Producto> lista = List.of(
                // 1. Mercancía Frágil
                new Producto("PROD-FRAG-01", "Jarrón de Cerámica Vidriada", TipoMercancia.FRAGIL, 8),
                new Producto("PROD-FRAG-02", "Set Copas de Cristal Bohemio", TipoMercancia.FRAGIL, 12),
                new Producto("PROD-FRAG-03", "Espejo Biselado Decorativo", TipoMercancia.FRAGIL, 4),

                // 2. Mercancía Pesada
                new Producto("PROD-PES-01", "Refrigeradora No-Frost 400L", TipoMercancia.PESADO, 2),
                new Producto("PROD-PES-02", "Lavadora Industrial 20kg", TipoMercancia.PESADO, 2),
                new Producto("PROD-PES-03", "Generador Eléctrico a Diésel", TipoMercancia.PESADO, 1),

                // 3. Mercancía Electrónica
                new Producto("PROD-ELEC-01", "Smartphone Ultra 5G 256GB", TipoMercancia.ELECTRONICO, 20),
                new Producto("PROD-ELEC-02", "Laptop Gamer Core i9 16\"", TipoMercancia.ELECTRONICO, 10),
                new Producto("PROD-ELEC-03", "Servidor Rack 2U Enterprise", TipoMercancia.ELECTRONICO, 3),

                // 4. Mercancía Perecedera
                new Producto("PROD-PER-01", "Lote Vacunas Termosensibles", TipoMercancia.PERECEDERO, 15),
                new Producto("PROD-PER-02", "Caja Salmón Fresco Chileno", TipoMercancia.PERECEDERO, 8),
                new Producto("PROD-PER-03", "Frutas Finas Seleccionadas", TipoMercancia.PERECEDERO, 12),

                // 5. Mercancía Voluminosa
                new Producto("PROD-VOL-01", "Sofá Modular 3 Cuerpos", TipoMercancia.VOLUMINOSO, 1),
                new Producto("PROD-VOL-02", "Colchón Ortopédico King Size", TipoMercancia.VOLUMINOSO, 1),
                new Producto("PROD-VOL-03", "Mesa de Comedor de Roble", TipoMercancia.VOLUMINOSO, 1),

                // 6. Mercancía General
                new Producto("PROD-GEN-01", "Caja de Libros Académicos", TipoMercancia.GENERAL, 20),
                new Producto("PROD-GEN-02", "Fardo de Ropa por Mayor", TipoMercancia.GENERAL, 25),
                new Producto("PROD-GEN-03", "Lote Calzado Deportivo", TipoMercancia.GENERAL, 15)
        );

        Map<String, Producto> mapa = new LinkedHashMap<>();
        for (Producto p : lista) {
            mapa.put(p.id(), p);
        }

        TODOS_LOS_PRODUCTOS = Collections.unmodifiableList(lista);
        PRODUCTOS_POR_ID = Collections.unmodifiableMap(mapa);
    }

    private CatalogoProductos() {
    }

    public static List<Producto> obtenerTodos() {
        return TODOS_LOS_PRODUCTOS;
    }

    public static Optional<Producto> buscarPorId(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(PRODUCTOS_POR_ID.get(id));
    }

    public static Producto obtenerPorId(String id) {
        return buscarPorId(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado en catálogo: " + id));
    }

    public static List<Producto> obtenerPorTipo(TipoMercancia tipo) {
        return TODOS_LOS_PRODUCTOS.stream()
                .filter(p -> p.tipoMercancia() == tipo)
                .toList();
    }
}
