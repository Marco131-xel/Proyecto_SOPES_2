package gt.usac.redxela.api;

import gt.usac.redxela.api.dto.*;
import gt.usac.redxela.config.ConfiguracionSimulacion;
import gt.usac.redxela.deadlock.InformeConflicto;
import gt.usac.redxela.modelo.*;
import gt.usac.redxela.planificacion.PoliticaAtencion;
import gt.usac.redxela.proceso.CoordinadorSimulacion;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controlador REST y Server-Sent Events (SSE) central de REDXela (R8.1).
 * 
 * Expone la telemetría en tiempo real y todos los comandos de control del simulador.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SimulacionController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SimulacionController.class);
    private final gt.usac.redxela.eventos.RegistroEventos eventos;
    private final CoordinadorSimulacion coordinador;
    private final GestorClientes gestorClientes;
    private final List<SseEmitter> emisoresSSE = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService schedulerSSE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hilo-publicador-sse");
        t.setDaemon(true);
        return t;
    });

    public SimulacionController(CoordinadorSimulacion coordinador, GestorClientes gestorClientes, gt.usac.redxela.eventos.RegistroEventos eventos) {
        this.eventos = eventos;
        this.coordinador = coordinador;
        this.gestorClientes = gestorClientes;

        // Tarea periódica de transmisión de estado por SSE cada 500 ms (R16, DEC-002)
        schedulerSSE.scheduleWithFixedDelay(() -> {
            try { transmitirEstadoSSE(); }
            catch (Exception e) { log.error("Fallo al publicar estado SSE; se reintentará", e); }
        }, ConfiguracionSimulacion.INTERVALO_PUBLICACION_ESTADO_MS, ConfiguracionSimulacion.INTERVALO_PUBLICACION_ESTADO_MS, TimeUnit.MILLISECONDS);
    }

    private void transmitirEstadoSSE() {
        if (emisoresSSE.isEmpty()) return;

        EstadoSistema estado = coordinador.capturarEstado();
        List<SseEmitter> muertos = new ArrayList<>();

        for (SseEmitter emisor : emisoresSSE) {
            try {
                emisor.send(SseEmitter.event()
                        .name("estado")
                        .data(estado, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                emisor.complete();
                muertos.add(emisor);
            }
        }
        emisoresSSE.removeAll(muertos);
    }

    /** Cierra el publicador y las conexiones al apagar el servidor. */
    @jakarta.annotation.PreDestroy
    public void cerrar() {
        schedulerSSE.shutdownNow();
        for (SseEmitter emisor : emisoresSSE) emisor.complete();
        emisoresSSE.clear();
    }

    private NivelServicio validarNivel(int nivel) {
        if (nivel < 1 || nivel > 5) throw new IllegalArgumentException("El nivel debe estar entre 1 y 5");
        return NivelServicio.fromNivel(nivel);
    }

    // --- Endpoints de Telemetría ---

    @GetMapping(value = "/estado/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter conectarStreamSSE() {
        SseEmitter emisor = new SseEmitter(0L); // Sin timeout automático
        emisoresSSE.add(emisor);

        emisor.onCompletion(() -> emisoresSSE.remove(emisor));
        emisor.onTimeout(() -> emisoresSSE.remove(emisor));
        emisor.onError(e -> emisoresSSE.remove(emisor));

        // Enviar estado inmediato al conectarse
        try {
            emisor.send(SseEmitter.event()
                    .name("estado")
                    .data(coordinador.capturarEstado(), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emisor.complete();
            emisoresSSE.remove(emisor);
        }

        return emisor;
    }

    @GetMapping("/estado")
    public ResponseEntity<EstadoSistema> obtenerEstadoPuntual() {
        return ResponseEntity.ok(coordinador.capturarEstado());
    }

    // --- Catálogo y Clientes ---

    @GetMapping("/catalogo")
    public ResponseEntity<Map<String, Object>> obtenerCatalogo() {
        return ResponseEntity.ok(Map.of(
                "productos", CatalogoProductos.obtenerTodos(),
                "tiposMercancia", Arrays.stream(TipoMercancia.values())
                        .map(t -> Map.of(
                                "tipo", t.name(),
                                "nombre", t.getNombre(),
                                "descripcion", t.getDescripcion(),
                                "recursosRequeridos", t.getRecursosRequeridos().stream().map(Enum::name).toList()
                        )).toList()
        ));
    }

    @GetMapping("/clientes")
    public ResponseEntity<List<ClienteDTO>> obtenerClientes() {
        return ResponseEntity.ok(gestorClientes.obtenerTodos().stream().map(ClienteDTO::de).toList());
    }

    @PostMapping("/clientes")
    public ResponseEntity<ClienteDTO> crearCliente(@RequestBody CrearClienteRequest req) {
        if (req.nombre() == null || req.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente no puede estar vacío");
        }
        if (req.correo() == null || !req.correo().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("El correo no es válido");
        }
        NivelServicio nivel = validarNivel(req.nivelServicio());
        Cliente nuevo = gestorClientes.registrarCliente(req.nombre(), req.correo(), nivel);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteDTO.de(nuevo));
    }

    // --- Pedidos Manuales ---

    @PostMapping("/pedidos")
    public ResponseEntity<Map<String, Object>> crearPedidoManual(@RequestBody CrearPedidoRequest req) {
        Cliente cliente = gestorClientes.buscarPorId(req.clienteId())
                .orElseThrow(() -> new gt.usac.redxela.excepciones.ClienteNoEncontradoException("Cliente no encontrado: " + req.clienteId()));

        NivelServicio nivel = req.nivelServicio() != null
                ? validarNivel(req.nivelServicio())
                : cliente.nivelContratado();

        if (req.lineas() == null || req.lineas().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe contener al menos un producto");
        }

        List<LineaPedido> lineas = new ArrayList<>();
        for (CrearPedidoRequest.LineaDTO l : req.lineas()) {
            if (l == null) throw new IllegalArgumentException("Línea de pedido nula");
            Producto prod = CatalogoProductos.buscarPorId(l.productoId())
                    .orElseThrow(() -> new NoSuchElementException("Producto no existe: " + l.productoId()));
            lineas.add(new LineaPedido(prod, l.cantidad()));
        }

        List<TipoRecurso> ordenPersonalizado = null;
        if (req.ordenAdquisicionPersonalizado() != null && !req.ordenAdquisicionPersonalizado().isEmpty()) {
            ordenPersonalizado = req.ordenAdquisicionPersonalizado().stream()
                    .map(TipoRecurso::valueOf)
                    .toList();
        }

        long ahoraSim = coordinador.getSimClock().ahoraSimuladoSegundos();
        Pedido nuevo = new Pedido(Pedido.generarSiguienteId(), cliente, nivel, lineas, ahoraSim, ordenPersonalizado);

        coordinador.getDespachador().registrarPedido(nuevo);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "mensaje", "Pedido manual registrado exitosamente",
                "pedidoId", nuevo.getId(),
                "nivel", nuevo.getNivel().getNombre(),
                "totalUnidades", nuevo.getTotalUnidades()
        ));
    }

    // --- Recepción de Mercancía ---

    @PostMapping("/recepcion")
    public ResponseEntity<Map<String, Object>> registrarLlegadaCamion(@RequestBody RegistrarRecepcionRequest req) {
        Producto prod = CatalogoProductos.buscarPorId(req.productoId())
                .orElseThrow(() -> new NoSuchElementException("Producto no válido: " + req.productoId()));

        if (req.cantidad() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }

        var camion = coordinador.getRecepcion().registrarLlegada(prod, req.cantidad());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "mensaje", "Camión recibido en patio de maniobras",
                "camionId", camion.id(),
                "producto", prod.nombre(),
                "cantidad", req.cantidad()
        ));
    }

    // --- Control del Generador ---

    @PostMapping("/generador/iniciar")
    public ResponseEntity<Map<String, String>> iniciarGenerador() {
        coordinador.getGeneradorPedidos().iniciar();
        return ResponseEntity.ok(Map.of("mensaje", "Generador automático iniciado"));
    }

    @PostMapping("/generador/pausar")
    public ResponseEntity<Map<String, String>> pausarGenerador() {
        coordinador.getGeneradorPedidos().pausar();
        return ResponseEntity.ok(Map.of("mensaje", "Generador pausado"));
    }

    @PostMapping("/generador/reanudar")
    public ResponseEntity<Map<String, String>> reanudarGenerador() {
        coordinador.getGeneradorPedidos().reanudar();
        return ResponseEntity.ok(Map.of("mensaje", "Generador reanudado"));
    }

    @PostMapping("/generador/detener")
    public ResponseEntity<Map<String, String>> detenerGenerador() {
        coordinador.getGeneradorPedidos().detener();
        return ResponseEntity.ok(Map.of("mensaje", "Generador detenido"));
    }

    @PutMapping("/generador/config")
    public ResponseEntity<Map<String, Object>> configurarGenerador(@RequestBody ConfigGeneradorRequest req) {
        if (req.tasaPedidosPorMinuto() != null && (!Double.isFinite(req.tasaPedidosPorMinuto()) || req.tasaPedidosPorMinuto() <= 0)) {
            throw new IllegalArgumentException("La tasa debe ser un número positivo");
        }
        if (req.tasaPedidosPorMinuto() != null) {
            coordinador.getGeneradorPedidos().setTasaPedidosPorMinutoSimulado(req.tasaPedidosPorMinuto());
        }
        if (req.semilla() != null) {
            coordinador.getGeneradorPedidos().setSemilla(req.semilla());
        }
        return ResponseEntity.ok(Map.of(
                "mensaje", "Configuración de generador actualizada",
                "tasa", coordinador.getGeneradorPedidos().getTasaPedidosPorMinutoSimulado()
        ));
    }

    // --- Políticas de Planificación ---

    @GetMapping("/politicas")
    public ResponseEntity<List<gt.usac.redxela.planificacion.GestorPoliticas.PoliticaInfo>> listarPoliticas() {
        return ResponseEntity.ok(coordinador.getGestorPoliticas().obtenerTodas());
    }

    @PutMapping("/politica")
    public ResponseEntity<Map<String, String>> cambiarPolitica(@RequestBody CambiarPoliticaRequest req) {
        PoliticaAtencion nueva = coordinador.getGestorPoliticas().obtenerPorNombre(req.politica());
        coordinador.getDespachador().setPoliticaActiva(nueva);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Política cambiada exitosamente",
                "politicaActiva", nueva.nombre(),
                "descripcion", nueva.descripcion()
        ));
    }

    // --- Reloj Simulado ---

    @PutMapping("/simulacion/velocidad")
    public ResponseEntity<Map<String, Object>> cambiarVelocidadReloj(@RequestBody VelocidadRelojRequest req) {
        if (req.factor() != null && (!Double.isFinite(req.factor()) || req.factor() < 1 || req.factor() > 600)) {
            throw new IllegalArgumentException("El factor debe estar entre 1 y 600");
        }
        if (req.factor() != null) {
            coordinador.getSimClock().setFactor(req.factor());
        }
        if (req.pausar() != null) {
            if (req.pausar()) coordinador.getSimClock().pausar();
            else coordinador.getSimClock().reanudar();
        }
        return ResponseEntity.ok(Map.of(
                "factor", coordinador.getSimClock().getFactor(),
                "pausado", coordinador.getSimClock().isPausado()
        ));
    }

    // --- Prevención e Interbloqueos (Deadlock) ---

    @PutMapping("/prevencion")
    public ResponseEntity<Map<String, Object>> alternarPrevencion(@RequestBody PrevencionDeadlockRequest req) {
        if (req.activar() == null) throw new IllegalArgumentException("Debe especificar activar");
        if (req.activar() && coordinador.getDetectorDeadlock().ejecutarDeteccion().hayConflicto()) {
            throw new IllegalStateException("Resuelva primero el conflicto activo");
        }
        if (req.activar()) coordinador.getGestorDeadlock().restaurarMantenimientoYPrevencion();
        else coordinador.getGestorRecursos().setPrevencionDeadlock(false);
        coordinador.getDespachador().despertar();
        return ResponseEntity.ok(Map.of(
                "mensaje", "Modo de prevención actualizado",
                "prevencionActiva", coordinador.getGestorRecursos().isPrevencionDeadlock()
        ));
    }

    @PostMapping("/escenarios/deadlock")
    public ResponseEntity<Map<String, Object>> provocarDeadlock() {
        if (!coordinador.getDespachador().getPedidosEnProceso().isEmpty()
                || coordinador.getListaEspera().size() > 0) {
            throw new IllegalStateException("Pause el generador y espere a que no queden pedidos antes de provocar el escenario");
        }
        if (coordinador.getInventario().getDisponible("PROD-PES-01") < 2
                || coordinador.getInventario().getDisponible("PROD-VOL-01") < 1) {
            throw new IllegalStateException("Se necesitan 2 unidades de PROD-PES-01 y 1 de PROD-VOL-01 para el escenario");
        }
        coordinador.getGeneradorPedidos().pausar();
        var pedidos = coordinador.getGestorDeadlock().provocarEscenarioDeadlock();
        return ResponseEntity.ok(Map.of(
                "mensaje", "Escenario de interbloqueo provocado con éxito",
                "pedidosInvolucrados", pedidos.stream().map(Pedido::getId).toList()
        ));
    }

    @GetMapping("/conflicto")
    public ResponseEntity<InformeConflicto> obtenerInformeConflicto() {
        return ResponseEntity.ok(coordinador.getDetectorDeadlock().ejecutarDeteccion());
    }

    @PostMapping("/conflicto/resolver")
    public ResponseEntity<Map<String, String>> resolverConflicto(@RequestBody ResolverConflictoRequest req) {
        if (req.ganadorId() == null || req.ganadorId().isBlank()) {
            throw new IllegalArgumentException("Debe especificar el ID del pedido ganador");
        }
        coordinador.getGestorDeadlock().resolverConflictoManual(req.ganadorId());
        eventos.registrar("RESOLUCION", "Ganador elegido: " + req.ganadorId());
        return ResponseEntity.ok(Map.of(
                "mensaje", "Conflicto resuelto exitosamente. El proceso ganador continúa y la víctima retrocedió a espera."
        ));
    }
}
