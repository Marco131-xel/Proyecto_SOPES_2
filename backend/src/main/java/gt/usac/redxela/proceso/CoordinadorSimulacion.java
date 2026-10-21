package gt.usac.redxela.proceso;

import gt.usac.redxela.almacen.Almacen;
import gt.usac.redxela.almacen.AlmacenSnapshot;
import gt.usac.redxela.api.dto.EstadoSistema;
import gt.usac.redxela.config.SimClock;
import gt.usac.redxela.deadlock.DetectorDeadlock;
import gt.usac.redxela.deadlock.GestorEscenarioDeadlock;
import gt.usac.redxela.deadlock.InformeConflicto;
import gt.usac.redxela.estadisticas.Estadisticas;
import gt.usac.redxela.estadisticas.EstadisticasSnapshot;
import gt.usac.redxela.generador.GeneradorPedidos;
import gt.usac.redxela.inventario.Inventario;
import gt.usac.redxela.inventario.InventarioSnapshot;
import gt.usac.redxela.modelo.CatalogoProductos;
import gt.usac.redxela.modelo.Pedido;
import gt.usac.redxela.modelo.Producto;
import gt.usac.redxela.planificacion.Despachador;
import gt.usac.redxela.planificacion.GestorPoliticas;
import gt.usac.redxela.planificacion.ListaEspera;
import gt.usac.redxela.recepcion.Recepcion;
import gt.usac.redxela.recepcion.RecepcionSnapshot;
import gt.usac.redxela.recursos.GestorRecursos;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinador central del ciclo de vida y telemetría de REDXela.
 * 
 * Arranca automáticamente los hilos de Despacho, Recepción, Inventario y Detección
 * de Interbloqueos al iniciar el servidor Spring Boot, y construye la foto consolidada
 * EstadoSistema transmitida por Server-Sent Events (SSE) a la UI cada 500 ms (R16).
 */
@Service
public class CoordinadorSimulacion {

    private final gt.usac.redxela.eventos.RegistroEventos eventos;
    private final Despachador despachador;
    private final Recepcion recepcion;
    private final Inventario inventario;
    private final DetectorDeadlock detectorDeadlock;
    private final GeneradorPedidos generadorPedidos;
    private final GestorEscenarioDeadlock gestorDeadlock;
    private final GestorRecursos gestorRecursos;
    private final GestorPoliticas gestorPoliticas;
    private final ListaEspera listaEspera;
    private final Almacen almacen;
    private final Estadisticas estadisticas;
    private final SimClock simClock;

    public CoordinadorSimulacion(
            Despachador despachador,
            Recepcion recepcion,
            Inventario inventario,
            DetectorDeadlock detectorDeadlock,
            GeneradorPedidos generadorPedidos,
            GestorEscenarioDeadlock gestorDeadlock,
            GestorRecursos gestorRecursos,
            GestorPoliticas gestorPoliticas,
            ListaEspera listaEspera,
            Almacen almacen,
            Estadisticas estadisticas,
            SimClock simClock,
            gt.usac.redxela.eventos.RegistroEventos eventos
    ) {
        this.eventos = eventos;
        this.despachador = despachador;
        this.recepcion = recepcion;
        this.inventario = inventario;
        this.detectorDeadlock = detectorDeadlock;
        this.generadorPedidos = generadorPedidos;
        this.gestorDeadlock = gestorDeadlock;
        this.gestorRecursos = gestorRecursos;
        this.gestorPoliticas = gestorPoliticas;
        this.listaEspera = listaEspera;
        this.almacen = almacen;
        this.estadisticas = estadisticas;
        this.simClock = simClock;
    }

    @PostConstruct
    public void iniciarSistema() {
        despachador.setInventario(inventario);

        // Pre-cargar stock inicial para que los pedidos puedan procesarse desde el arranque
        precargarInventarioInicial();

        // Iniciar hilos del sistema
        despachador.iniciar();
        recepcion.iniciar();
        inventario.iniciar();
        detectorDeadlock.iniciar();
    }

    @PreDestroy
    public void detenerSistema() {
        generadorPedidos.detener();
        detectorDeadlock.detener();
        inventario.detener();
        recepcion.detener();
        despachador.detener();
    }

    private void precargarInventarioInicial() {
        // Dos ubicaciones por producto; la carga inicial recorre el mismo flujo que la UI.
        for (Producto p : CatalogoProductos.obtenerTodos()) {
            recepcion.registrarLlegada(p, p.unidadesPorUbicacion() * 2);
        }
    }

    /**
     * Construye una fotografía consistente e inmutable del estado del sistema.
     */
    public EstadoSistema capturarEstado() {
        long ahoraSim = simClock.ahoraSimuladoSegundos();
        int enCola = listaEspera.size();
        int enProceso = despachador.getPedidosEnProceso().size();

        // Construir detalles de pedidos en proceso
        List<EstadoSistema.PedidoDetalleDTO> enProcesoDTOs = new ArrayList<>();
        for (Pedido p : despachador.getPedidosEnProceso().values()) {
            var recursoSolicitado = p.getRecursoSolicitadoActual();
            long duracionSeg = p.getDuracionEstimadaSegundos();
            long restante = Math.max(0, Math.round(duracionSeg * (1 - p.getProgresoPorcentaje() / 100.0)));

            enProcesoDTOs.add(new EstadoSistema.PedidoDetalleDTO(
                    p.getId(),
                    p.getCliente().nombre(),
                    p.getNivel().getNivel(),
                    p.getNivel().getNombre(),
                    p.getTotalUnidades(),
                    p.getEstado().name(),
                    p.getProgresoPorcentaje(),
                    p.getInstanteInicio() != null ? p.getInstanteInicio() : 0,
                    duracionSeg,
                    restante,
                    p.getUnidadesRecursosRetenidas(),
                    recursoSolicitado != null ? recursoSolicitado.getNombreLegible() : null,
                    p.getVecesReencolado(),
                    p.getLineas().stream().map(l -> l.producto().tipoMercancia().getNombre()).distinct().toList()
            ));
        }

        // Construir detalles de últimos completados
        List<EstadoSistema.PedidoDetalleDTO> completadosDTOs = new ArrayList<>();
        for (Pedido p : despachador.getPedidosCompletados()) {
            if (completadosDTOs.size() >= 20) break; // Mostramos los últimos 20 en UI
            completadosDTOs.add(new EstadoSistema.PedidoDetalleDTO(
                    p.getId(),
                    p.getCliente().nombre(),
                    p.getNivel().getNivel(),
                    p.getNivel().getNombre(),
                    p.getTotalUnidades(),
                    p.getEstado().name(),
                    100.0,
                    p.getInstanteInicio() != null ? p.getInstanteInicio() : 0,
                    p.getDuracionEstimadaSegundos(),
                    0,
                    p.getUnidadesRecursosRetenidas(),
                    null,
                    p.getVecesReencolado(),
                    p.getLineas().stream().map(l -> l.producto().tipoMercancia().getNombre()).distinct().toList()
            ));
        }

        AlmacenSnapshot almacenSnap = almacen.snapshot(inventario.getLotesPendientesEspacioCount());
        RecepcionSnapshot recepcionSnap = recepcion.snapshot();
        InventarioSnapshot inventarioSnap = inventario.snapshot();
        EstadisticasSnapshot statsSnap = estadisticas.snapshot(ahoraSim, enCola, enProceso);
        InformeConflicto conflicto = detectorDeadlock.getUltimoInforme();

        var politica = despachador.getPoliticaActiva();
        return new EstadoSistema(
                ahoraSim,
                simClock.getFactor(),
                simClock.isPausado(),
                politica.nombre(),
                politica.descripcion(),
                gestorRecursos.isPrevencionDeadlock(),
                generadorPedidos.isEjecutando(),
                generadorPedidos.isPausado(),
                generadorPedidos.getTasaPedidosPorMinutoSimulado(),
                gestorRecursos.snapshot(),
                enProcesoDTOs,
                listaEspera.snapshot(politica, ahoraSim),
                completadosDTOs,
                almacenSnap,
                recepcionSnap,
                inventarioSnap,
                statsSnap,
                conflicto,
                eventos.snapshot()
        );
    }

    public Despachador getDespachador() {
        return despachador;
    }

    public Recepcion getRecepcion() {
        return recepcion;
    }

    public Inventario getInventario() {
        return inventario;
    }

    public DetectorDeadlock getDetectorDeadlock() {
        return detectorDeadlock;
    }

    public GeneradorPedidos getGeneradorPedidos() {
        return generadorPedidos;
    }

    public GestorEscenarioDeadlock getGestorDeadlock() {
        return gestorDeadlock;
    }

    public GestorRecursos getGestorRecursos() {
        return gestorRecursos;
    }

    public GestorPoliticas getGestorPoliticas() {
        return gestorPoliticas;
    }

    public ListaEspera getListaEspera() {
        return listaEspera;
    }

    public Almacen getAlmacen() {
        return almacen;
    }

    public Estadisticas getEstadisticas() {
        return estadisticas;
    }

    public SimClock getSimClock() {
        return simClock;
    }
}
