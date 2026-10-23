export type TipoRecurso = 'ENCARGADO' | 'EMPAQUE' | 'CARGA' | 'MONTACARGAS' | 'CONTROL_CALIDAD' | 'ESCANER';

export interface UnidadSnapshot {
  id: string;
  nombreLegible: string;
  tipo: TipoRecurso;
  duenoActual: string | null;
  enMantenimiento: boolean;
  instanteAsignacion: number;
}

export interface PoolSnapshot {
  tipo: TipoRecurso;
  capacidadTotal: number;
  disponibles: number;
  unidades: UnidadSnapshot[];
}

export interface PedidoDetalleDTO {
  id: string;
  clienteNombre: string;
  nivelNumero: number;
  nivelNombre: string;
  totalUnidades: number;
  estado: string;
  progresoPorcentaje: number;
  instanteInicio: number;
  duracionEstimadaSegundos: number;
  tiempoRestanteSegundos: number;
  recursosRetenidos: string[];
  recursoSolicitado: string | null;
  vecesReencolado: number;
  tiposMercancia: string[];
}

export interface PedidoEnEsperaSnapshot {
  posicion: number;
  id: string;
  clienteNombre: string;
  nivelNumero: number;
  nivelNombre: string;
  totalUnidades: number;
  estado: string;
  instanteCreacion: number;
  esperaActualSegundos: number;
  esperaObjetivoSegundos: number;
  tiempoRestanteSLASegundos: number;
  vecesReencolado: number;
  recursosRequeridos: string[];
}

export interface UbicacionSnapshot {
  id: string;
  pasillo: number;
  nivel: number;
  posicion: number;
  indiceLineal: number;
  productoId: string | null;
  productoNombre: string | null;
  tipoMercancia: string | null;
  cantidadOcupada: number;
  capacidadMaxima: number;
  libre: boolean;
  loteId: string | null;
}

export interface LoteSnapshot {
  id: string;
  productoId: string;
  productoNombre: string;
  cantidadTotal: number;
  instanteIngreso: number;
  tablaAsignacion: { ubicacionId: string; unidades: number }[];
  fragmentacionInternaUnidades: number;
}

export interface AlmacenSnapshot {
  totalUbicaciones: number;
  ubicacionesLibres: number;
  ubicacionesOcupadas: number;
  porcentajeOcupacion: number;
  fragmentacionInternaTotalUnidades: number;
  lotesPendientesEspacio: number;
  ubicaciones: UbicacionSnapshot[];
  lotesActivos: LoteSnapshot[];
}

export interface Camion {
  id: string;
  producto: {
    id: string;
    nombre: string;
    tipoMercancia: string;
    unidadesPorUbicacion: number;
  };
  cantidad: number;
  instanteLlegada: number;
}

export interface RecepcionSnapshot {
  camionesEnPatio: number;
  mensajesEnColaRecepcion: number;
  capacidadMaximaCola: number;
  camionesProcesadosTotal: number;
  escanerOcupado: boolean;
  camionesEnPatioDetalle: Camion[];
}

export interface ExistenciaProductoSnapshot {
  productoId: string;
  productoNombre: string;
  tipoMercancia: string;
  existenciasFisicas: number;
  reservado: number;
  disponibleParaPedidos: number;
}

export interface InventarioSnapshot {
  totalMensajesProcesados: number;
  lotesPendientesEspacio: number;
  existencias: ExistenciaProductoSnapshot[];
}

export interface EstadisticaNivelSnapshot {
  nivel: number;
  nombreNivel: string;
  completados: number;
  esperaPromedioSegundos: number;
  esperaMaximaSegundos: number;
  procesamientoPromedioSegundos: number;
  cumplieronSLA: number;
  fallaronSLA: number;
  porcentajeCumplimientoSLA: number;
}

export interface MuestraHistoricaCola {
  instanteSimulado: number;
  pedidosEnCola: number;
  pedidosEnProceso: number;
}

export interface EstadisticasSnapshot {
  pedidosCreados: number;
  pedidosCompletados: number;
  pedidosReencolados: number;
  pedidosEnColaActuales: number;
  pedidosEnProcesoActuales: number;
  esperaPromedioGlobalSegundos: number;
  procesamientoPromedioGlobalSegundos: number;
  porcentajeCumplimientoSLAGlobal: number;
  throughputPedidosPorMinutoSim: number;
  metricasPorNivel: EstadisticaNivelSnapshot[];
  serieHistoricaCola: MuestraHistoricaCola[];
}

export interface AristaAsignacion {
  nodoProceso: string;
  nodoRecurso: string;
  tipoArista: 'RETIENE' | 'SOLICITUD';
}

export interface InformeConflicto {
  hayConflicto: boolean;
  pedidosInvolucrados: string[];
  recursosDisputados: string[];
  grafoAsignacion: AristaAsignacion[];
  explicacion: string;
  instanteDeteccion: number;
}

export interface EstadoSistema {
  tiempoSimuladoSegundos: number;
  factorReloj: number;
  relojPausado: boolean;
  politicaActiva: string;
  descripcionPolitica: string;
  prevencionDeadlock: boolean;
  generadorActivo: boolean;
  generadorPausado: boolean;
  tasaGenerador: number;
  recursos: Record<TipoRecurso, PoolSnapshot>;
  pedidosEnProceso: PedidoDetalleDTO[];
  listaEspera: PedidoEnEsperaSnapshot[];
  ultimosCompletados: PedidoDetalleDTO[];
  almacen: AlmacenSnapshot;
  recepcion: RecepcionSnapshot;
  inventario: InventarioSnapshot;
  estadisticas: EstadisticasSnapshot;
  conflicto: InformeConflicto;
  eventos: Evento[];
}

export interface Cliente {
  id: string;
  nombre: string;
  correo: string;
  nivelContratado: {
    nivel: number;
    nombre: string;
    esperaObjetivoMinutos: number;
  };
}

export interface Producto {
  id: string;
  nombre: string;
  tipoMercancia: string;
  unidadesPorUbicacion: number;
}

export interface TipoMercanciaInfo {
  tipo: string;
  nombre: string;
  descripcion: string;
  recursosRequeridos: string[];
}

export interface Evento {
  id: number;
  instanteSimulado: number;
  tipo: string;
  mensaje: string;
}

export interface ConfiguracionComparacion {
  semilla: number;
  cantidadPedidos: number;
  tasaLlegadasPorMinuto: number;
}

export interface ResultadoPolitica {
  politica: string;
  descripcion: string;
  pedidosCompletados: number;
  horizonteSimuladoSegundos: number;
  esperaPromedioSegundos: number;
  esperaMaximaSegundos: number;
  porcentajeSLA: number;
  throughputPedidosPorMinuto: number;
  ocupacionMediaPorcentaje: number;
  pedidosConInanicion: number;
  niveles: EstadisticaNivelSnapshot[];
  ocupacionPorRecurso: Record<TipoRecurso, number>;
}

export interface ResultadoComparacion {
  configuracion: ConfiguracionComparacion;
  huellaCarga: string;
  alcance: string;
  politicas: ResultadoPolitica[];
  conclusion: string;
}

export interface EstadoComparacion {
  id: number;
  estado: 'SIN_EJECUTAR' | 'EJECUTANDO' | 'COMPLETADA' | 'ERROR';
  configuracion: ConfiguracionComparacion | null;
  resultado: ResultadoComparacion | null;
  error: string | null;
}
