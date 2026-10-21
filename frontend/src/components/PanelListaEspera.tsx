import React from 'react';
import { PedidoEnEsperaSnapshot, EstadoSistema } from '../types/simulacion';
import { ListOrdered, AlertTriangle, CheckCircle2, Clock } from 'lucide-react';

interface Props {
  listaEspera: PedidoEnEsperaSnapshot[] | undefined;
  descripcionPolitica?: string;
  recursos?: EstadoSistema['recursos'];
}

export const PanelListaEspera: React.FC<Props> = ({ listaEspera = [], descripcionPolitica, recursos }) => {
  const getBadgeNivel = (nivel: number) => {
    switch (nivel) {
      case 1: return 'nivel-1';
      case 2: return 'nivel-2';
      case 3: return 'nivel-3';
      case 4: return 'nivel-4';
      default: return 'nivel-5';
    }
  };

  const calcularSemaforo = (restanteSLA: number, objetivoSLA: number) => {
    if (restanteSLA < 0 || (restanteSLA === 0 && objetivoSLA > 0)) {
      return { clase: 'sla-violado', texto: 'SLA Excedido', icon: <AlertTriangle size={14} /> };
    }
    const ratio = restanteSLA / Math.max(1, objetivoSLA);
    if (ratio < 0.3) {
      return { clase: 'sla-riesgo', texto: 'Crítico', icon: <AlertTriangle size={14} /> };
    }
    return { clase: 'sla-optimo', texto: 'En Tiempo', icon: <CheckCircle2 size={14} /> };
  };

  return (
    <div className="card-panel">
      <div className="panel-header">
        <h2 className="panel-titulo">
          <ListOrdered size={18} className="icono-panel" />
          <span>Cola de Despacho (Lista de Espera)</span>
        </h2>
        <span className="panel-contador">{listaEspera.length} en espera</span>
      </div>

      <p className="panel-subinfo">{descripcionPolitica}</p>
      {listaEspera.length === 0 ? (
        <div className="vacio-mensaje">
          No hay pedidos pendientes en la cola. El despachador está en reposo (espera en Monitor `Condition`).
        </div>
      ) : (
        <div className="tabla-responsive">
          <table className="tabla-redxela">
            <thead>
              <tr>
                <th>#</th>
                <th>ID Pedido</th>
                <th>Cliente</th>
                <th>Nivel / SLA</th>
                <th>Unidades</th>
                <th>Espera / Objetivo</th>
                <th>Semáforo SLA</th>
                <th>Motivo de espera</th>
                <th>Reencolado</th>
              </tr>
            </thead>
            <tbody>
              {listaEspera.map((p) => {
                const semaforo = calcularSemaforo(p.tiempoRestanteSLASegundos, p.esperaObjetivoSegundos);
                return (
                  <tr key={p.id}>
                    <td>
                      <span className="badge-posicion">{p.posicion}</span>
                    </td>
                    <td>
                      <span className="codigo-pedido">{p.id}</span>
                    </td>
                    <td>
                      <span className="cliente-nombre">{p.clienteNombre}</span>
                    </td>
                    <td>
                      <span className={`badge-nivel ${getBadgeNivel(p.nivelNumero)}`}>
                        L{p.nivelNumero} - {p.nivelNombre}
                      </span>
                    </td>
                    <td>{p.totalUnidades} uds</td>
                    <td>
                      <div className="tiempo-celda">
                        <Clock size={13} className="icono-subtle" />
                        <span>
                          {p.esperaActualSegundos}s / {p.esperaObjetivoSegundos}s
                        </span>
                      </div>
                    </td>
                    <td>
                      <span className={`badge-semaforo ${semaforo.clase}`}>
                        {semaforo.icon}
                        <span>{semaforo.texto}</span>
                        {p.tiempoRestanteSLASegundos > 0 ? (
                          <small>({p.tiempoRestanteSLASegundos}s)</small>
                        ) : (
                          <small>({Math.abs(p.tiempoRestanteSLASegundos)}s tarde)</small>
                        )}
                      </span>
                    </td>
                    <td>{p.estado === 'EN_ESPERA_STOCK' ? 'Stock insuficiente' :
                      `Recursos: ${p.recursosRequeridos.filter(r => recursos?.[r as keyof typeof recursos]?.disponibles === 0).join(', ') || 'pendiente de asignación'}`}</td>
                    <td>
                      {p.vecesReencolado > 0 ? (
                        <span className="badge-reencolado" title="Reencolado por falta transitoria de stock/recurso">
                          {p.vecesReencolado}x
                        </span>
                      ) : (
                        <span className="texto-muted">-</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
