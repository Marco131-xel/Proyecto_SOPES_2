package gt.usac.redxela.api;

import gt.usac.redxela.comparacion.ConfiguracionComparacion;
import gt.usac.redxela.comparacion.ServicioComparacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** POST admite el trabajo; GET consulta su estado y último resultado. */
@RestController
@RequestMapping("/api/comparacion")
public class ComparacionController {
    private final ServicioComparacion servicio;

    public ComparacionController(ServicioComparacion servicio) { this.servicio = servicio; }

    /** Los campos omitidos usan valores predeterminados; el dominio valida los presentes. */
    public record ComparacionRequest(Long semilla, Integer cantidadPedidos, Double tasaLlegadasPorMinuto) {
        ConfiguracionComparacion configurar() {
            var base = ConfiguracionComparacion.porDefecto();
            return new ConfiguracionComparacion(semilla != null ? semilla : base.semilla(),
                    cantidadPedidos != null ? cantidadPedidos : base.cantidadPedidos(),
                    tasaLlegadasPorMinuto != null ? tasaLlegadasPorMinuto : base.tasaLlegadasPorMinuto());
        }
    }

    @PostMapping
    public ResponseEntity<ServicioComparacion.EstadoComparacion> ejecutar(@RequestBody(required = false) ComparacionRequest req) {
        return ResponseEntity.accepted().body(servicio.iniciar(req == null ? ConfiguracionComparacion.porDefecto() : req.configurar()));
    }

    @GetMapping
    public ServicioComparacion.EstadoComparacion consultar() { return servicio.obtenerEstado(); }
}
