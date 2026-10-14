package gt.usac.redxela.planificacion;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registro central de políticas de planificación disponibles en REDXela.
 * 
 * Permite seleccionar e intercambiar en caliente (R5) el algoritmo de atención
 * activo en el Despachador mediante su identificador textual.
 */
@Component
public class GestorPoliticas {

    private final Map<String, PoliticaAtencion> politicas = new LinkedHashMap<>();
    private final PoliticaAtencion politicaPorDefecto;

    public GestorPoliticas(
            PoliticaPrioridadEstricta estricta,
            PoliticaPrioridadConAging aging,
            PoliticaMenorCantidadPrimero sjf,
            PoliticaEDF edf
    ) {
        politicas.put(estricta.nombre(), estricta);
        politicas.put(aging.nombre(), aging);
        politicas.put(sjf.nombre(), sjf);
        politicas.put(edf.nombre(), edf);
        this.politicaPorDefecto = estricta;
    }

    public List<PoliticaInfo> obtenerTodas() {
        return politicas.values().stream()
                .map(p -> new PoliticaInfo(p.nombre(), p.descripcion()))
                .toList();
    }

    public Optional<PoliticaAtencion> buscarPorNombre(String nombre) {
        if (nombre == null) return Optional.empty();
        return Optional.ofNullable(politicas.get(nombre.toUpperCase().trim()));
    }

    public PoliticaAtencion obtenerPorNombre(String nombre) {
        return buscarPorNombre(nombre)
                .orElseThrow(() -> new IllegalArgumentException("Política no reconocida: " + nombre));
    }

    public PoliticaAtencion getPoliticaPorDefecto() {
        return politicaPorDefecto;
    }

    public record PoliticaInfo(String nombre, String descripcion) {}
}
