package gt.usac.redxela.modelo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Define los 6 tipos de mercancía en el centro de distribución REDXela.
 * 
 * Cada tipo requiere una combinación distinta de recursos compartidos (R7),
 * generando puntos de contención deliberados (R8):
 * - FRÁGIL y PERECEDERO compiten por ENCARGADO + CONTROL_CALIDAD.
 * - PESADO y VOLUMINOSO compiten por MONTACARGAS + CARGA (par utilizado para el interbloqueo).
 */
public enum TipoMercancia {
    FRAGIL(
            "Frágil",
            "Vidrio, cerámica y artículos de decoración de alta delicadeza",
            EnumSet.of(TipoRecurso.ENCARGADO, TipoRecurso.CONTROL_CALIDAD, TipoRecurso.EMPAQUE)
    ),
    PESADO(
            "Pesado",
            "Electrodomésticos, maquinaria pesada y herramientas de gran masa",
            EnumSet.of(TipoRecurso.ENCARGADO, TipoRecurso.MONTACARGAS, TipoRecurso.CARGA)
    ),
    ELECTRONICO(
            "Electrónico",
            "Dispositivos de alto valor que requieren trazabilidad serializada y escaneo",
            EnumSet.of(TipoRecurso.CONTROL_CALIDAD, TipoRecurso.ESCANER, TipoRecurso.CARGA)
    ),
    PERECEDERO(
            "Perecedero",
            "Alimentos y medicamentos que requieren cadena de frío y revisión urgente",
            EnumSet.of(TipoRecurso.ENCARGADO, TipoRecurso.MONTACARGAS, TipoRecurso.CONTROL_CALIDAD)
    ),
    VOLUMINOSO(
            "Voluminoso",
            "Muebles, colchones y paquetes de gran dimensión geométrica",
            EnumSet.of(TipoRecurso.MONTACARGAS, TipoRecurso.EMPAQUE, TipoRecurso.CARGA)
    ),
    GENERAL(
            "General",
            "Ropa, calzado, libros y accesorios de consumo cotidiano",
            EnumSet.of(TipoRecurso.ENCARGADO, TipoRecurso.EMPAQUE, TipoRecurso.CARGA)
    );

    private final String nombre;
    private final String descripcion;
    private final Set<TipoRecurso> recursosRequeridos;

    TipoMercancia(String nombre, String descripcion, Set<TipoRecurso> recursosRequeridos) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.recursosRequeridos = Collections.unmodifiableSet(recursosRequeridos);
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Set<TipoRecurso> getRecursosRequeridos() {
        return recursosRequeridos;
    }
}
