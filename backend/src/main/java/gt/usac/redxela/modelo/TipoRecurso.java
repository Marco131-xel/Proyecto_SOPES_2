package gt.usac.redxela.modelo;

import gt.usac.redxela.config.ConfiguracionSimulacion;

/**
 * Enumeración de los recursos compartidos fijos del centro de distribución REDXela.
 * 
 * Cada tipo define su capacidad inicial fija de unidades operativas:
 * - ENCARGADO: 10 unidades
 * - EMPAQUE: 6 estaciones
 * - CARGA: 2 áreas de carga
 * - MONTACARGAS: 3 montacargas
 * - CONTROL_CALIDAD: 4 estaciones
 * - ESCANER: 1 escáner (recurso exclusivo compartido entre Recepción y Despacho)
 */
public enum TipoRecurso {
    ENCARGADO("Encargado de Bodega", "ENC", ConfiguracionSimulacion.CAPACIDAD_ENCARGADOS),
    EMPAQUE("Estación de Empaque", "EMP", ConfiguracionSimulacion.CAPACIDAD_ESTACIONES_EMPAQUE),
    CARGA("Área de Carga", "CRG", ConfiguracionSimulacion.CAPACIDAD_AREAS_CARGA),
    MONTACARGAS("Montacargas", "MTC", ConfiguracionSimulacion.CAPACIDAD_MONTACARGAS),
    CONTROL_CALIDAD("Control de Calidad", "CC", ConfiguracionSimulacion.CAPACIDAD_CONTROL_CALIDAD),
    ESCANER("Escáner", "ESC", ConfiguracionSimulacion.CAPACIDAD_ESCANER);

    private final String nombreLegible;
    private final String prefijo;
    private final int capacidadDefecto;

    TipoRecurso(String nombreLegible, String prefijo, int capacidadDefecto) {
        this.nombreLegible = nombreLegible;
        this.prefijo = prefijo;
        this.capacidadDefecto = capacidadDefecto;
    }

    public String getNombreLegible() {
        return nombreLegible;
    }

    public String getPrefijo() {
        return prefijo;
    }

    public int getCapacidadDefecto() {
        return capacidadDefecto;
    }
}
