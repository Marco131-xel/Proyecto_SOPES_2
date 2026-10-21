package gt.usac.redxela.api.dto;

import gt.usac.redxela.modelo.Cliente;

/** Expone nivel, nombre y SLA sin depender de la serialización de enums del dominio. */
public record ClienteDTO(String id, String nombre, String correo, NivelDTO nivelContratado) {
    public static ClienteDTO de(Cliente c) {
        var n = c.nivelContratado();
        return new ClienteDTO(c.id(), c.nombre(), c.correo(), new NivelDTO(n.getNivel(), n.getNombre(), n.getEsperaObjetivoMinutos()));
    }
    public record NivelDTO(int nivel, String nombre, long esperaObjetivoMinutos) {}
}
