import {
  EstadoSistema,
  Cliente,
  Producto,
  TipoMercanciaInfo,
  InformeConflicto
} from '../types/simulacion';

const BASE_URL = '/api';

/** Todos los comandos comprueban HTTP y muestran el mensaje de validación del servidor. */
async function leerRespuesta<T = any>(res: Response): Promise<T> {
  const datos = await res.json();
  if (!res.ok) throw new Error(datos.mensaje || `Error ${res.status}: ${res.statusText}`);
  return datos as T;
}

export interface CrearPedidoLineaReq {
  productoId: string;
  cantidad: number;
}

export interface CrearPedidoReq {
  clienteId: string;
  nivelServicio?: number;
  lineas: CrearPedidoLineaReq[];
  ordenAdquisicionPersonalizado?: string[];
}

export const api = {
  // EventSource reintenta automáticamente; el sondeo mantiene la pantalla actualizada entretanto.
  conectarStreamSSE(
    onEstado: (estado: EstadoSistema) => void,
    onError?: (error: Event) => void,
    onModo?: (modo: 'sse' | 'polling' | 'reconectando') => void
  ): () => void {
    let cerrado = false;
    let sseActivo = false;
    let sondeando = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const abortador = new AbortController();
    const eventSource = new EventSource(`${BASE_URL}/estado/stream`);
    const sondear = async () => {
      timer = undefined;
      if (cerrado || sseActivo || sondeando) return;
      sondeando = true;
      try {
        const estado = await leerRespuesta<EstadoSistema>(await fetch(`${BASE_URL}/estado`, { signal: abortador.signal }));
        if (!cerrado && !sseActivo) { onEstado(estado); onModo?.('polling'); }
      } catch {
        if (!cerrado && !sseActivo) onModo?.('reconectando');
      } finally {
        sondeando = false;
        if (!cerrado && !sseActivo) timer = setTimeout(sondear, 1500);
      }
    };
    eventSource.addEventListener('estado', (event: MessageEvent) => {
      try {
        const data: EstadoSistema = JSON.parse(event.data);
        if (cerrado) return;
        sseActivo = true;
        clearTimeout(timer);
        timer = undefined;
        onEstado(data);
        onModo?.('sse');
      } catch { onError?.(event); }
    });
    eventSource.onerror = (err) => {
      sseActivo = false;
      onError?.(err);
      onModo?.('reconectando');
      // Un único ciclo de sondeo por conexión, incluso ante errores repetidos.
    };
    const vigilancia = setInterval(() => {
      if (!sseActivo && !sondeando && timer === undefined) {
        timer = setTimeout(sondear, 0);
      }
    }, 1500);
    void sondear();
    return () => {
      cerrado = true;
      clearTimeout(timer);
      clearInterval(vigilancia);
      abortador.abort();
      eventSource.close();
    };
  },

  // Obtener snapshot puntual de estado
  async obtenerEstado(): Promise<EstadoSistema> {
    const res = await fetch(`${BASE_URL}/estado`);
    return leerRespuesta(res);
  },

  // Catálogo de productos y tipos de mercancía
  async obtenerCatalogo(): Promise<{ productos: Producto[]; tiposMercancia: TipoMercanciaInfo[] }> {
    const res = await fetch(`${BASE_URL}/catalogo`);
    return leerRespuesta(res);
  },

  // Clientes
  async obtenerClientes(): Promise<Cliente[]> {
    const res = await fetch(`${BASE_URL}/clientes`);
    return leerRespuesta(res);
  },

  async crearCliente(nombre: string, correo: string, nivelServicio: number): Promise<Cliente> {
    const res = await fetch(`${BASE_URL}/clientes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nombre, correo, nivelServicio }),
    });
    return leerRespuesta(res);
  },

  // Pedidos
  async crearPedidoManual(req: CrearPedidoReq): Promise<{ mensaje: string; pedidoId: string }> {
    const res = await fetch(`${BASE_URL}/pedidos`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    return leerRespuesta(res);
  },

  // Recepción de mercancía
  async registrarLlegadaCamion(productoId: string, cantidad: number): Promise<any> {
    const res = await fetch(`${BASE_URL}/recepcion`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productoId, cantidad }),
    });
    return leerRespuesta(res);
  },

  // Generador estocástico
  async iniciarGenerador(): Promise<any> {
    const res = await fetch(`${BASE_URL}/generador/iniciar`, { method: 'POST' });
    return leerRespuesta(res);
  },

  async pausarGenerador(): Promise<any> {
    const res = await fetch(`${BASE_URL}/generador/pausar`, { method: 'POST' });
    return leerRespuesta(res);
  },

  async reanudarGenerador(): Promise<any> {
    const res = await fetch(`${BASE_URL}/generador/reanudar`, { method: 'POST' });
    return leerRespuesta(res);
  },

  async detenerGenerador(): Promise<any> {
    const res = await fetch(`${BASE_URL}/generador/detener`, { method: 'POST' });
    return leerRespuesta(res);
  },

  async configurarGenerador(tasaPedidosPorMinuto?: number, semilla?: number): Promise<any> {
    const res = await fetch(`${BASE_URL}/generador/config`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tasaPedidosPorMinuto, semilla }),
    });
    return leerRespuesta(res);
  },

  // Políticas
  async listarPoliticas(): Promise<{ nombre: string; descripcion: string }[]> {
    const res = await fetch(`${BASE_URL}/politicas`);
    return leerRespuesta(res);
  },

  async cambiarPolitica(politica: string): Promise<any> {
    const res = await fetch(`${BASE_URL}/politica`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ politica }),
    });
    return leerRespuesta(res);
  },

  // Reloj
  async cambiarVelocidad(factor?: number, pausar?: boolean): Promise<any> {
    const res = await fetch(`${BASE_URL}/simulacion/velocidad`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ factor, pausar }),
    });
    return leerRespuesta(res);
  },

  // Prevención e interbloqueos
  async alternarPrevencion(activar: boolean): Promise<any> {
    const res = await fetch(`${BASE_URL}/prevencion`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ activar }),
    });
    return leerRespuesta(res);
  },

  async provocarDeadlock(): Promise<any> {
    const res = await fetch(`${BASE_URL}/escenarios/deadlock`, { method: 'POST' });
    return leerRespuesta(res);
  },

  async obtenerConflicto(): Promise<InformeConflicto> {
    const res = await fetch(`${BASE_URL}/conflicto`);
    return leerRespuesta(res);
  },

  async resolverConflicto(ganadorId: string): Promise<any> {
    const res = await fetch(`${BASE_URL}/conflicto/resolver`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ganadorId }),
    });
    return leerRespuesta(res);
  },
};
