import React, { useEffect, useState } from 'react';
import { Cliente, Producto } from '../types/simulacion';
import { CrearPedidoReq } from '../services/api';
import { UserPlus, ShoppingBag, Plus, Trash2, CheckCircle2 } from 'lucide-react';

interface Props {
  clientes: Cliente[];
  productos: Producto[];
  onCrearCliente: (nombre: string, correo: string, nivelServicio: number) => Promise<void>;
  onCrearPedido: (req: CrearPedidoReq) => Promise<void>;
}

export const PanelRegistroManual: React.FC<Props> = ({
  clientes = [],
  productos = [],
  onCrearCliente,
  onCrearPedido,
}) => {
  // Estado formulario Cliente
  const [nombreCliente, setNombreCliente] = useState('');
  const [correoCliente, setCorreoCliente] = useState('');
  const [nivelCliente, setNivelCliente] = useState<number>(3);
  const [mensajeCliente, setMensajeCliente] = useState<string | null>(null);

  // Estado formulario Pedido
  const [clienteSeleccionado, setClienteSeleccionado] = useState<string>(
    clientes[0]?.id || ''
  );
  const [lineas, setLineas] = useState<{ productoId: string; cantidad: number }[]>([
    { productoId: productos[0]?.id || '', cantidad: 5 },
  ]);
  const [ordenPersonalizado, setOrdenPersonalizado] = useState<string>('');
  const [mensajePedido, setMensajePedido] = useState<string | null>(null);

  // Los catálogos llegan de forma asíncrona después del primer render.
  useEffect(() => {
    if (!clienteSeleccionado && clientes.length) setClienteSeleccionado(clientes[0].id);
  }, [clientes, clienteSeleccionado]);
  useEffect(() => {
    if (productos.length) setLineas(prev => prev.map(l => l.productoId ? l : { ...l, productoId: productos[0].id }));
  }, [productos]);

  // Guardar nuevo cliente
  const handleGuardarCliente = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nombreCliente.trim() || !correoCliente.trim()) return;
    try {
      await onCrearCliente(nombreCliente, correoCliente, nivelCliente);
      setMensajeCliente('¡Cliente creado exitosamente!');
      setNombreCliente('');
      setCorreoCliente('');
      setTimeout(() => setMensajeCliente(null), 3000);
    } catch (err: any) {
      setMensajeCliente(`Error: ${err.message}`);
    }
  };

  // Manejo de líneas de pedido
  const agregarLinea = () => {
    if (productos.length > 0) {
      setLineas([...lineas, { productoId: productos[0].id, cantidad: 5 }]);
    }
  };

  const eliminarLinea = (index: number) => {
    if (lineas.length > 1) {
      setLineas(lineas.filter((_, i) => i !== index));
    }
  };

  const actualizarLinea = (index: number, campo: 'productoId' | 'cantidad', valor: any) => {
    const copia = [...lineas];
    copia[index] = { ...copia[index], [campo]: valor };
    setLineas(copia);
  };

  // Guardar nuevo pedido
  const handleGuardarPedido = async (e: React.FormEvent) => {
    e.preventDefault();
    const cId = clienteSeleccionado || clientes[0]?.id;
    if (!cId) {
      setMensajePedido('Error: Debe seleccionar un cliente');
      return;
    }

    try {
      let orden: string[] | undefined = undefined;
      if (ordenPersonalizado.trim()) {
        orden = ordenPersonalizado
          .split(',')
          .map((s) => s.trim().toUpperCase())
          .filter(Boolean);
      }

      await onCrearPedido({
        clienteId: cId,
        lineas,
        ordenAdquisicionPersonalizado: orden,
      });

      setMensajePedido('¡Pedido ingresado exitosamente a la cola de despacho!');
      setLineas([{ productoId: productos[0]?.id || '', cantidad: 5 }]);
      setOrdenPersonalizado('');
      setTimeout(() => setMensajePedido(null), 3000);
    } catch (err: any) {
      setMensajePedido(`Error: ${err.message}`);
    }
  };

  const nivelesServicio = [
    { nivel: 1, nombre: 'Exprés (SLA inmediato)' },
    { nivel: 2, nombre: 'Prioritario (SLA 10 min)' },
    { nivel: 3, nombre: 'Estándar (SLA 30 min)' },
    { nivel: 4, nombre: 'Programado (SLA 60 min)' },
    { nivel: 5, nombre: 'Económico (SLA 120 min)' },
  ];

  return (
    <div className="card-panel">
      <div className="panel-header">
        <div>
          <h2 className="panel-titulo">
            <UserPlus size={18} className="icono-panel" />
            <span>Gestión Manual: Clientes y Pedidos Personalizados</span>
          </h2>
          <span className="panel-subinfo">
            Inyección manual de procesos y clientes para pruebas dirigidas
          </span>
        </div>
      </div>

      <div className="registro-grid">
        {/* Formulario Cliente */}
        <div className="form-seccion-card">
          <h3 className="subtitulo-seccion">
            <UserPlus size={16} />
            <span>Registrar Nuevo Cliente</span>
          </h3>

          <form onSubmit={handleGuardarCliente} className="formulario-redxela">
            <div className="form-campo">
              <label>Nombre de la Empresa / Cliente:</label>
              <input
                type="text"
                required
                placeholder="Ej. Farmacias de Occidente"
                value={nombreCliente}
                onChange={(e) => setNombreCliente(e.target.value)}
                className="input-redxela"
              />
            </div>

            <div className="form-campo">
              <label>Correo Electrónico:</label>
              <input
                type="email"
                required
                placeholder="contacto@empresa.gt"
                value={correoCliente}
                onChange={(e) => setCorreoCliente(e.target.value)}
                className="input-redxela"
              />
            </div>

            <div className="form-campo">
              <label>Nivel de Servicio Contratado (SLA):</label>
              <select
                value={nivelCliente}
                onChange={(e) => setNivelCliente(Number(e.target.value))}
                className="select-redxela"
              >
                {nivelesServicio.map((n) => (
                  <option key={n.nivel} value={n.nivel}>
                    Nivel {n.nivel}: {n.nombre}
                  </option>
                ))}
              </select>
            </div>

            {mensajeCliente && (
              <div className={`mensaje-feedback ${mensajeCliente?.startsWith('Error:') ? 'error' : 'exito'}`}>
                <CheckCircle2 size={16} />
                <span>{mensajeCliente}</span>
              </div>
            )}

            <button type="submit" className="btn-primario">
              Registrar Cliente
            </button>
          </form>
        </div>

        {/* Formulario Pedido */}
        <div className="form-seccion-card">
          <h3 className="subtitulo-seccion">
            <ShoppingBag size={16} />
            <span>Crear Pedido Manual (Proceso)</span>
          </h3>

          <form onSubmit={handleGuardarPedido} className="formulario-redxela">
            <div className="form-campo">
              <label>Cliente Emisor:</label>
              <select
                value={clienteSeleccionado}
                onChange={(e) => setClienteSeleccionado(e.target.value)}
                className="select-redxela"
              >
                {clientes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nombre} (Nivel {c.nivelContratado.nivel}: {c.nivelContratado.nombre})
                  </option>
                ))}
              </select>
            </div>

            <div className="lineas-pedido-contenedor">
              <div className="lineas-header">
                <label>Líneas de Productos:</label>
                <button
                  type="button"
                  className="btn-secundario-sm"
                  onClick={agregarLinea}
                >
                  <Plus size={14} />
                  <span>Agregar Producto</span>
                </button>
              </div>

              {lineas.map((linea, idx) => (
                <div key={idx} className="linea-item-form">
                  <select
                    value={linea.productoId}
                    onChange={(e) => actualizarLinea(idx, 'productoId', e.target.value)}
                    className="select-redxela flex-2"
                  >
                    {productos.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.nombre} ({p.tipoMercancia})
                      </option>
                    ))}
                  </select>

                  <input
                    type="number"
                    min="1"
                    max="100"
                    value={linea.cantidad}
                    onChange={(e) =>
                      actualizarLinea(idx, 'cantidad', Math.max(1, Number(e.target.value)))
                    }
                    className="input-redxela flex-1"
                  />

                  {lineas.length > 1 && (
                    <button
                      type="button"
                      className="btn-peligro-icon"
                      onClick={() => eliminarLinea(idx)}
                      title="Eliminar línea"
                    >
                      <Trash2 size={16} />
                    </button>
                  )}
                </div>
              ))}
            </div>

            <div className="form-campo">
              <label>Orden Personalizado de Recursos (Opcional para pruebas):</label>
              <input
                type="text"
                placeholder="Ej. MONTACARGAS, CARGA, EMPAQUE"
                value={ordenPersonalizado}
                onChange={(e) => setOrdenPersonalizado(e.target.value)}
                className="input-redxela"
              />
              <small className="texto-muted">
                Separar por comas. Déjelo vacío para orden estándar por jerarquía.
              </small>
            </div>

            {mensajePedido && (
              <div className={`mensaje-feedback ${mensajePedido?.startsWith('Error:') ? 'error' : 'exito'}`}>
                <CheckCircle2 size={16} />
                <span>{mensajePedido}</span>
              </div>
            )}

            <button type="submit" className="btn-primario">
              Enviar a Despacho
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
