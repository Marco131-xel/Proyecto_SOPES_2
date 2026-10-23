import React, { useEffect, useState, useCallback } from 'react';
import { api, CrearPedidoReq } from './services/api';
import {
  EstadoSistema,
  Cliente,
  Producto,
  TipoMercanciaInfo
} from './types/simulacion';
import { PanelComparacion } from './components/PanelComparacion';
import { PanelEventos } from './components/PanelEventos';
import { BarraControl } from './components/BarraControl';
import { PanelRecursos } from './components/PanelRecursos';
import { PanelEnProceso } from './components/PanelEnProceso';
import { PanelListaEspera } from './components/PanelListaEspera';
import { PanelAlmacen } from './components/PanelAlmacen';
import { PanelRecepcion } from './components/PanelRecepcion';
import { PanelEstadisticas } from './components/PanelEstadisticas';
import { PanelDeadlock } from './components/PanelDeadlock';
import { PanelRegistroManual } from './components/PanelRegistroManual';
import {
  Activity,
  Layers,
  BarChart3,
  UserPlus,
  AlertTriangle
} from 'lucide-react';

type TabTipo = 'operaciones' | 'almacen' | 'metricas' | 'manual';

export const App: React.FC = () => {
  const [estado, setEstado] = useState<EstadoSistema | null>(null);
  const [modoConexion, setModoConexion] = useState<'sse' | 'polling' | 'reconectando'>('reconectando');
  const [error, setError] = useState<string | null>(null);
  const [tabActiva, setTabActiva] = useState<TabTipo>('operaciones');

  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [catalogo, setCatalogo] = useState<{
    productos: Producto[];
    tiposMercancia: TipoMercanciaInfo[];
  }>({ productos: [], tiposMercancia: [] });

  // Cargar datos iniciales (catálogo y clientes)
  const cargarCatalogos = useCallback(async () => {
    try {
      const [cats, cls] = await Promise.all([
        api.obtenerCatalogo(),
        api.obtenerClientes(),
      ]);
      setCatalogo(cats);
      setClientes(cls);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudieron cargar los catálogos');
    }
  }, []);

  useEffect(() => {
    cargarCatalogos();

    // Conectar a transmisión SSE en tiempo real
    const cancelarSSE = api.conectarStreamSSE(
      (nuevoEstado) => {
        setEstado(nuevoEstado);
      },
      (err) => {
        console.warn('Conexión SSE perdida o reintentando...', err);
        setModoConexion('reconectando');
      },
      setModoConexion
    );

    return () => {
      cancelarSSE();
    };
  }, [cargarCatalogos]);

  // Manejadores de Control
  const handleCambioVelocidad = async (factor: number) => {
    try {
      await api.cambiarVelocidad(factor);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
    }
  };

  const handlePausarReanudar = async (pausar: boolean) => {
    try {
      await api.cambiarVelocidad(undefined, pausar);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
    }
  };

  const handleCambioPolitica = async (politica: string) => {
    try {
      await api.cambiarPolitica(politica);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
    }
  };

  const handleTogglePrevencion = async (activar: boolean) => {
    try {
      await api.alternarPrevencion(activar);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
    }
  };

  const handleToggleGenerador = async (iniciar: boolean) => {
    try {
      if (iniciar) {
        if (!estado?.generadorActivo) {
          await api.iniciarGenerador();
        } else {
          await api.reanudarGenerador();
        }
      } else {
        await api.pausarGenerador();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
    }
  };

  const handleConfigurarGenerador = async (tasa: number, semilla?: number) => {
    try {
      await api.configurarGenerador(tasa, semilla);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
    }
  };

  const handleProvocarDeadlock = async () => {
    try {
      await api.provocarDeadlock();
      // Opcional: enfocar pestaña de operaciones para ver el conflicto inmediatamente
      setTabActiva('operaciones');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
    }
  };

  const handleResolverDeadlock = async (ganadorId: string) => {
    try {
      await api.resolverConflicto(ganadorId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
    }
  };

  const handleEnviarCamion = async (productoId: string, cantidad: number) => {
    try {
      await api.registrarLlegadaCamion(productoId, cantidad);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo ejecutar la operación');
      throw err;
    }
  };

  const handleCrearCliente = async (nombre: string, correo: string, nivel: number) => {
    const nuevo = await api.crearCliente(nombre, correo, nivel);
    setClientes((prev) => [...prev, nuevo]);
  };

  const handleCrearPedido = async (req: CrearPedidoReq) => {
    await api.crearPedidoManual(req);
  };

  const hayDeadlock = estado?.conflicto?.hayConflicto ?? false;

  return (
    <div className="redxela-app">
      {/* Barra de Control y Telemetría Global */}
      <BarraControl
        estado={estado}
        conectado={modoConexion === 'sse'}
        modoConexion={modoConexion}
        onCambioVelocidad={handleCambioVelocidad}
        onPausarReanudar={handlePausarReanudar}
        onCambioPolitica={handleCambioPolitica}
        onTogglePrevencion={handleTogglePrevencion}
        onToggleGenerador={handleToggleGenerador}
        onConfigurarGenerador={handleConfigurarGenerador}
        onProvocarDeadlock={handleProvocarDeadlock}
      />

      {error && <div className="mensaje-feedback error" role="alert">
        <span>{error}</span>
        <button className="btn-secundario-sm" onClick={() => { setError(null); void cargarCatalogos(); }}>Reintentar / cerrar</button>
      </div>}
      {modoConexion !== 'sse' && <p className="estado-conexion" role="status">
        {modoConexion === 'polling' ? 'Actualizando por sondeo; reconectando SSE…' : 'Sin conexión: los datos visibles pueden estar desactualizados. Reintentando…'}
      </p>}
      {/* Banner Persistente de Alerta Global en caso de Deadlock */}
      {hayDeadlock && (
        <div className="banner-alerta-global">
          <div className="banner-alerta-info">
            <AlertTriangle size={20} />
            <span>
              <strong>¡INTERBLOQUEO ACTIVO!</strong> Procesos en espera circular:{' '}
              {estado?.conflicto.pedidosInvolucrados.join(', ')}
            </span>
          </div>
          <button
            className="btn-banner-resolver"
            onClick={() => setTabActiva('operaciones')}
          >
            Ver Grafo RAG y Resolver
          </button>
        </div>
      )}

      {/* Navegación por Pestañas */}
      <nav className="nav-pestanas">
        <button
          className={`tab-nav ${tabActiva === 'operaciones' ? 'activo' : ''}`}
          onClick={() => setTabActiva('operaciones')}
        >
          <Activity size={16} />
          <span>Operaciones en Vivo</span>
          {hayDeadlock && <span className="tab-alerta-dot" title="Interbloqueo detectado" />}
        </button>

        <button
          className={`tab-nav ${tabActiva === 'almacen' ? 'activo' : ''}`}
          onClick={() => setTabActiva('almacen')}
        >
          <Layers size={16} />
          <span>Almacén e Inventario</span>
        </button>

        <button
          className={`tab-nav ${tabActiva === 'metricas' ? 'activo' : ''}`}
          onClick={() => setTabActiva('metricas')}
        >
          <BarChart3 size={16} />
          <span>Métricas y SLA</span>
        </button>

        <button
          className={`tab-nav ${tabActiva === 'manual' ? 'activo' : ''}`}
          onClick={() => setTabActiva('manual')}
        >
          <UserPlus size={16} />
          <span>Gestión Manual</span>
        </button>
      </nav>

      {/* Área Principal de Contenido según la pestaña */}
      <main className="main-contenido">
        {tabActiva === 'operaciones' && (
          <div>
            {/* Si hay conflicto, mostrar el panel de resolución en la cima */}
            {hayDeadlock && (
              <PanelDeadlock
                conflicto={estado?.conflicto}
                recursos={estado?.recursos}
                onResolver={handleResolverDeadlock}
              />
            )}

            <div className="grid-dos-columnas">
              <div>
                <PanelRecursos recursos={estado?.recursos} />
                <PanelEnProceso pedidos={estado?.pedidosEnProceso} />
              </div>

              <div>
                <PanelListaEspera listaEspera={estado?.listaEspera} descripcionPolitica={estado?.descripcionPolitica} recursos={estado?.recursos} />
                {!hayDeadlock && (
                  <PanelDeadlock
                    conflicto={estado?.conflicto}
                    recursos={estado?.recursos}
                    onResolver={handleResolverDeadlock}
                  />
                )}
              </div>
            </div>
          </div>
        )}

        {tabActiva === 'almacen' && (
          <div>
            <PanelAlmacen almacen={estado?.almacen} />
            <PanelRecepcion
              recepcion={estado?.recepcion}
              inventario={estado?.inventario}
              catalogoProductos={catalogo.productos}
              onEnviarCamion={handleEnviarCamion}
            />
          </div>
        )}

        {tabActiva === 'metricas' && (
          <>
            <PanelEstadisticas estadisticas={estado?.estadisticas} recursos={estado?.recursos} />
            <PanelComparacion />
          </>
        )}

        {tabActiva === 'manual' && (
          <PanelRegistroManual
            clientes={clientes}
            productos={catalogo.productos}
            onCrearCliente={handleCrearCliente}
            onCrearPedido={handleCrearPedido}
          />
        )}
      </main>
      <PanelEventos eventos={estado?.eventos} />
    </div>
  );
};

export default App;
