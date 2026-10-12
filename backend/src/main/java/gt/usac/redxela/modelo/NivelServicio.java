package gt.usac.redxela.modelo;

/**
 * Representa los niveles de servicio contratados por los clientes en REDXela.
 * 
 * Determina la prioridad de despacho y el tiempo límite objetivo de espera (SLA)
 * en minutos simulados según la especificación del centro de distribución:
 * - Nivel 1: Exprés (0 min, atención inmediata)
 * - Nivel 2: Prioritario (10 min)
 * - Nivel 3: Estándar (30 min)
 * - Nivel 4: Programado (60 min)
 * - Nivel 5: Económico (120 min)
 */
public enum NivelServicio {
    EXPRES(1, "Exprés", 0),
    PRIORITARIO(2, "Prioritario", 10),
    ESTANDAR(3, "Estándar", 30),
    PROGRAMADO(4, "Programado", 60),
    ECONOMICO(5, "Económico", 120);

    private final int nivel;
    private final String nombre;
    private final long esperaObjetivoMinutos;

    NivelServicio(int nivel, String nombre, long esperaObjetivoMinutos) {
        this.nivel = nivel;
        this.nombre = nombre;
        this.esperaObjetivoMinutos = esperaObjetivoMinutos;
    }

    public int getNivel() {
        return nivel;
    }

    public String getNombre() {
        return nombre;
    }

    public long getEsperaObjetivoMinutos() {
        return esperaObjetivoMinutos;
    }

    public long getEsperaObjetivoSegundos() {
        return esperaObjetivoMinutos * 60L;
    }

    /**
     * Obtiene el NivelServicio correspondiente a un valor numérico (1 a 5).
     * 
     * @param nivel Valor numérico del nivel.
     * @return Enum correspondiente o ESTANDAR si no coincide.
     */
    public static NivelServicio fromNivel(int nivel) {
        for (NivelServicio n : values()) {
            if (n.nivel == nivel) {
                return n;
            }
        }
        return ESTANDAR;
    }
}
