import React, { useEffect, useState } from 'react';
import { RecepcionSnapshot, InventarioSnapshot, Producto } from '../types/simulacion';
import { Truck, Scan, Box, PlusCircle, AlertCircle } from 'lucide-react';

interface Props {
  recepcion: RecepcionSnapshot | undefined;
  inventario: InventarioSnapshot | undefined;
  catalogoProductos: Producto[];
  onEnviarCamion: (productoId: string, cantidad: number) => Promise<void>;
}

export const PanelRecepcion: React.FC<Props> = ({
  recepcion,
  inventario,
  catalogoProductos = [],
  onEnviarCamion,
}) => {
  const [productoSeleccionado, setProductoSeleccionado] = useState<string>(
    catalogoProductos[0]?.id || ''
  );
  const [mensaje, setMensaje] = useState('');
  const [enviando, setEnviando] = useState(false);
  useEffect(() => {
    if (!productoSeleccionado && catalogoProductos.length) setProductoSeleccionado(catalogoProductos[0].id);
  }, [catalogoProductos, productoSeleccionado]);
  const [cantidad, setCantidad] = useState<number>(30);

  if (!recepcion || !inventario) {
    return (
      <div className="card-panel">
        <h2 className="panel-titulo">Recepción e Inventario</h2>
        <div className="cargando-vacio">Cargando estado del muelle de descarga...</div>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!productoSeleccionado || cantidad <= 0) return;
    setEnviando(true);
    try {
      await onEnviarCamion(productoSeleccionado, cantidad);
      setMensaje('Camión registrado en el patio');
    } catch (e) {
      setMensaje(`Error: ${e instanceof Error ? e.message : 'No se pudo recibir el camión'}`);
    } finally { setEnviando(false); }
  };

  const bufferOcupado = recepcion.mensajesEnColaRecepcion;
  const bufferMax = recepcion.capacidadMaximaCola || 20;
  const bufferPct = Math.round((bufferOcupado / bufferMax) * 100);

  return (
    <div className="card-panel">
      <div className="panel-header">
        <div>
          <h2 className="panel-titulo">
            <Truck size={18} className="icono-panel" />
            <span>Muelle de Recepción y Control de Inventario</span>
          </h2>
          <span className="panel-subinfo">
            Productor-Consumidor con Bounded Buffer (ArrayBlockingQueue con Contrapresión)
          </span>
        </div>

        {/* Estado del Escáner */}
        <div className="escaner-status-box">
          <Scan size={18} className={recepcion.escanerOcupado ? 'icono-ocupado' : 'icono-verde'} />
          <div>
            <span className="escaner-label">Escáner de Código de Barras:</span>
            <span className={`badge-escaner ${recepcion.escanerOcupado ? 'ocupado' : 'libre'}`}>
              {recepcion.escanerOcupado ? 'OCUPADO' : 'LIBRE'}
            </span>
          </div>
        </div>
      </div>

      <div className="recepcion-grid-superior">
        {/* Buffer Acotado IPC */}
        <div className="buffer-ipc-tarjeta">
          <div className="buffer-header">
            <span>Buffer IPC Bounded-Queue (Almacén Central)</span>
            <span className="badge-buffer">{bufferOcupado} / {bufferMax} slots</span>
          </div>

          <div className="barra-progreso-fondo">
            <div
              className={`barra-progreso-relleno ${
                bufferPct >= 80 ? 'lleno' : bufferPct >= 50 ? 'medio' : 'bajo'
              }`}
              style={{ width: `${bufferPct}%` }}
            />
          </div>

          <div className="buffer-footer">
            {bufferPct === 100 ? (
              <span className="aviso-contrapresion">
                <AlertCircle size={14} /> Contrapresión activa: hilo productor bloqueado en put()
              </span>
            ) : (
              <span className="texto-muted">Flujo nominal entre Recepción e Inventario</span>
            )}
          </div>
        </div>

        {/* Camiones en Patio */}
        <div className="camiones-patio-tarjeta">
          <div className="buffer-header">
            <span>Patio de Maniobras</span>
            <span className="badge-buffer">{recepcion.camionesEnPatio} camiones en espera</span>
          </div>
          <div className="camiones-chips">
            {recepcion.camionesEnPatioDetalle.length === 0 ? (
              <span className="texto-muted">No hay camiones esperando muelle.</span>
            ) : (
              recepcion.camionesEnPatioDetalle.map((c) => (
                <div key={c.id} className="chip-camion">
                  <Truck size={14} />
                  <span>{c.producto.nombre} ({c.cantidad}u)</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Formulario para Enviar Camión Manual */}
      <div className="formulario-camion-card">
        <h3 className="subtitulo-seccion">
          <PlusCircle size={16} />
          <span>Registrar Llegada de Camión al Patio</span>
        </h3>
        <form onSubmit={handleSubmit} className="form-inline-recepcion">
          <div className="form-campo">
            <label>Producto a Descargar:</label>
            <select
              value={productoSeleccionado}
              onChange={(e) => setProductoSeleccionado(e.target.value)}
              className="select-redxela"
            >
              {catalogoProductos.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nombre} ({p.tipoMercancia} - {p.unidadesPorUbicacion} u/slot)
                </option>
              ))}
            </select>
          </div>

          <div className="form-campo">
            <label>Cantidad de Unidades:</label>
            <input
              type="number"
              min="1"
              max="200"
              value={cantidad}
              onChange={(e) => setCantidad(Number(e.target.value))}
              className="input-redxela"
            />
          </div>

          <button type="submit" className="btn-primario" disabled={enviando || !productoSeleccionado}>
            <Truck size={16} />
            <span>Recibir Camión</span>
          </button>
        </form>
      </div>

      {mensaje && <p role="status" className={`mensaje-feedback ${mensaje.startsWith('Error:') ? 'error' : 'exito'}`}>{mensaje}</p>}
      <p className="panel-subinfo">Lotes pendientes de espacio: {inventario.lotesPendientesEspacio}</p>
      {/* Tabla de Existencias en Inventario */}
      <div className="inventario-existencias-seccion">
        <h3 className="subtitulo-seccion">
          <Box size={16} />
          <span>Existencias y Reservas Atómicas de Inventario</span>
        </h3>

        <div className="tabla-responsive">
          <table className="tabla-redxela tabla-compacta">
            <thead>
              <tr>
                <th>Producto</th>
                <th>Tipo Mercancía</th>
                <th>Físico en Almacén</th>
                <th>Reservado (Pedidos en Curso)</th>
                <th>Disponible para Despacho</th>
              </tr>
            </thead>
            <tbody>
              {inventario.existencias.map((e) => (
                <tr key={e.productoId}>
                  <td><strong>{e.productoNombre}</strong></td>
                  <td>
                    <span className="chip-tipo-mercancia">{e.tipoMercancia}</span>
                  </td>
                  <td>{e.existenciasFisicas} uds</td>
                  <td>{e.reservado} uds</td>
                  <td>
                    <span className={e.disponibleParaPedidos > 0 ? 'texto-verde' : 'texto-rojo'}>
                      <strong>{e.disponibleParaPedidos} uds</strong>
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
