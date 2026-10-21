import React from 'react';
import { PedidoDetalleDTO } from '../types/simulacion';
import { Activity, Clock, AlertCircle } from 'lucide-react';

interface Props {
  pedidos: PedidoDetalleDTO[] | undefined;
}

export const PanelEnProceso: React.FC<Props> = ({ pedidos = [] }) => {
  const getBadgeNivel = (nivel: number) => {
    switch (nivel) {
      case 1: return 'nivel-1';
      case 2: return 'nivel-2';
      case 3: return 'nivel-3';
      case 4: return 'nivel-4';
      default: return 'nivel-5';
    }
  };

  return (
    <div className="card-panel">
      <div className="panel-header">
        <h2 className="panel-titulo">
          <Activity size={18} className="icono-panel activo" />
          <span>Pedidos en Proceso (Hilos Activos)</span>
        </h2>
        <span className="panel-contador">{pedidos.length} ejecutando</span>
      </div>

      {pedidos.length === 0 ? (
        <div className="vacio-mensaje">
          No hay trabajadores activos en este momento. Los pedidos se asignarán según disponibilidad de recursos y stock.
        </div>
      ) : (
        <div className="tabla-responsive">
          <table className="tabla-redxela">
            <thead>
              <tr>
                <th>ID Pedido</th>
                <th>Cliente / SLA</th>
                <th>Mercancía / estado</th>
                <th>Progreso</th>
                <th>Tiempo Rest.</th>
                <th>Recursos Retenidos</th>
                <th>Bloqueo / Solicitud</th>
              </tr>
            </thead>
            <tbody>
              {pedidos.map((p) => (
                <tr key={p.id}>
                  <td>
                    <span className="codigo-pedido">{p.id}</span>
                  </td>
                  <td>
                    <div className="cliente-celda">
                      <span className="cliente-nombre">{p.clienteNombre}</span>
                      <span className={`badge-nivel ${getBadgeNivel(p.nivelNumero)}`}>
                        L{p.nivelNumero} - {p.nivelNombre}
                      </span>
                    </div>
                  </td>
                  <td>{p.tiposMercancia.join(', ')}<br /><small>{p.estado}</small></td>
                  <td style={{ minWidth: '140px' }}>
                    <div className="progreso-celda">
                      <div className="barra-progreso-fondo">
                        <div
                          className="barra-progreso-relleno progreso-azul"
                          style={{ width: `${Math.min(100, p.progresoPorcentaje)}%` }}
                        />
                      </div>
                      <span className="porcentaje-texto">{p.progresoPorcentaje.toFixed(0)}%</span>
                    </div>
                  </td>
                  <td>
                    <div className="tiempo-celda">
                      <Clock size={14} className="icono-subtle" />
                      <span>{p.tiempoRestanteSegundos}s</span>
                    </div>
                  </td>
                  <td>
                    <div className="chips-contenedor">
                      {p.recursosRetenidos.length === 0 ? (
                        <span className="texto-muted">Ninguno</span>
                      ) : (
                        p.recursosRetenidos.map((r) => (
                          <span key={r} className="chip-recurso retenido">
                            {r}
                          </span>
                        ))
                      )}
                    </div>
                  </td>
                  <td>
                    {p.recursoSolicitado ? (
                      <div className="bloqueo-aviso">
                        <AlertCircle size={14} className="icono-advertencia" />
                        <span>Esperando: <strong>{p.recursoSolicitado}</strong></span>
                      </div>
                    ) : (
                      <span className="texto-verde">Procesando</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
