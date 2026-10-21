import React from 'react';
import { InformeConflicto } from '../types/simulacion';
import { ShieldAlert, ShieldCheck, CheckCircle2, ArrowRight, RotateCcw } from 'lucide-react';

interface Props {
  conflicto: InformeConflicto | undefined;
  onResolver: (ganadorId: string) => void;
  recursos?: import('../types/simulacion').EstadoSistema['recursos'];
}

export const PanelDeadlock: React.FC<Props> = ({ conflicto, onResolver, recursos }) => {
  if (!conflicto || !conflicto.hayConflicto) {
    return (
      <div className="card-panel panel-deadlock-seguro">
        <div className="panel-header">
          <div className="deadlock-status-ok">
            <ShieldCheck size={20} className="icono-verde" />
            <div>
              <h3 className="titulo-sm">Detector de Interbloqueos (Matrices V, A, S, W, F)</h3>
              <p className="texto-muted">
                Ciclo de detección activo cada 500ms. Estado: <strong>Sin interbloqueos detectados</strong>.
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Agrupa unidades por tipo usando los nombres del snapshot para cerrar el ciclo del grafo.
  const normalizarRecurso = (nombre: string) => {
    const pool = Object.values(recursos || {}).find(p => p.unidades.some(u => u.nombreLegible === nombre));
    if (!pool) return nombre;
    const tipo = pool.tipo;
    const nombres: Record<string, string> = { MONTACARGAS: 'Montacargas', CARGA: 'Área de Carga', ENCARGADO: 'Encargado de Bodega', EMPAQUE: 'Estación de Empaque', CONTROL_CALIDAD: 'Control de Calidad', ESCANER: 'Escáner' };
    return nombres[tipo] || nombre;
  };
  const procesos = conflicto.pedidosInvolucrados;
  const tipos = [...new Set(conflicto.grafoAsignacion.map(a => normalizarRecurso(a.nodoRecurso)))];
  const alto = Math.max(procesos.length, tipos.length) * 90 + 40;
  const posicion = (id: string) => procesos.includes(id)
    ? { x: 130, y: 50 + procesos.indexOf(id) * 90 }
    : { x: 530, y: 50 + tipos.indexOf(id) * 90 };

  // Si hay conflicto activo:
  return (
    <div className="card-panel panel-deadlock-alerta">
      {/* Banner de Emergencia */}
      <div className="alerta-deadlock-header">
        <div className="alerta-icono-pulsante">
          <ShieldAlert size={26} />
        </div>
        <div className="alerta-texto">
          <h2>¡INTERBLOQUEO DETECTADO! (Espera Circular)</h2>
          <p>
            El algoritmo matricial multi-instancia ha identificado un conjunto de procesos
            mutuamente bloqueados que no pueden progresar.
          </p>
        </div>
      </div>

      {/* Explicación Técnica y Recursos en Disputa */}
      <div className="deadlock-cuerpo">
        <div className="deadlock-explicacion-box">
          <strong>Diagnóstico del Detector:</strong>
          <p>{conflicto.explicacion}</p>
        </div>

        {/* Recursos y Procesos Involucrados */}
        <div className="deadlock-metadatos">
          <div className="meta-col">
            <span className="meta-titulo">Procesos Involucrados en el Ciclo:</span>
            <div className="chips-contenedor">
              {conflicto.pedidosInvolucrados.map((pid) => (
                <span key={pid} className="chip-proceso-bloqueado">
                  {pid}
                </span>
              ))}
            </div>
          </div>

          <div className="meta-col">
            <span className="meta-titulo">Recursos en Disputa:</span>
            <div className="chips-contenedor">
              {conflicto.recursosDisputados.map((rec) => (
                <span key={rec} className="chip-recurso-disputado">
                  {rec}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Grafo de Asignación de Recursos (RAG) */}
        <div className="grafo-rag-seccion">
          <span className="meta-titulo">Grafo de Asignación de Recursos (Aristas RAG):</span>
          <svg viewBox={`0 0 680 ${alto}`} role="img" aria-label="Grafo de asignación: naranja retención, rojo solicitud" style={{ width: '100%', maxHeight: 420 }}>
            <defs><marker id="punta-rag" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto-start-reverse"><path d="M0,0 L8,4 L0,8" fill="#f8fafc" /></marker></defs>
            {conflicto.grafoAsignacion.map((a, i) => {
              const p = posicion(a.nodoProceso), r = posicion(normalizarRecurso(a.nodoRecurso));
              const solicita = a.tipoArista === 'SOLICITUD';
              return <path key={i} d={solicita ? `M ${p.x+70} ${p.y+8} L ${r.x-90} ${r.y+8}` : `M ${r.x-90} ${r.y-8} L ${p.x+70} ${p.y-8}`} stroke={solicita ? '#ef4444' : '#f59e0b'} strokeWidth="2" fill="none" markerEnd="url(#punta-rag)" />;
            })}
            {[...procesos, ...tipos].map(id => {
              const p = posicion(id);
              return <g key={id}><rect x={p.x-85} y={p.y-24} width="170" height="48" rx={procesos.includes(id) ? 24 : 4} fill="#1e293b" stroke="#94a3b8" /><text x={p.x} y={p.y+5} textAnchor="middle" fill="#f8fafc" fontSize="13">{id}</text></g>;
            })}
          </svg>
          <p className="texto-muted">Naranja: recurso retenido → pedido. Rojo: pedido → recurso solicitado.</p>
          <div className="aristas-lista">
            {conflicto.grafoAsignacion.map((arista, idx) => {
              const esSolicitud = arista.tipoArista === 'SOLICITUD';
              return (
                <div key={idx} className={`arista-card ${esSolicitud ? 'solicitud' : 'retiene'}`}>
                  {esSolicitud ? (
                    <>
                      <span className="nodo nodo-proceso">{arista.nodoProceso}</span>
                      <span className="flecha">solicita <ArrowRight size={14} /></span>
                      <span className="nodo nodo-recurso">{arista.nodoRecurso}</span>
                    </>
                  ) : (
                    <>
                      <span className="nodo nodo-recurso">{arista.nodoRecurso}</span>
                      <span className="flecha">asignado a <ArrowRight size={14} /></span>
                      <span className="nodo nodo-proceso">{arista.nodoProceso}</span>
                    </>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Panel de Resolución Manual */}
        <div className="resolucion-panel">
          <div className="resolucion-header">
            <RotateCcw size={18} />
            <h4>Resolución Manual por el Operador</h4>
          </div>
          <p className="resolucion-instruccion">
            Seleccione el proceso que debe ganar la contención y continuar con sus recursos.
            El otro proceso será abortado con Rollback, liberará todas sus concesiones de forma segura
            y volverá a la cola de espera para evitar inanición:
          </p>

          <div className="ganadores-botones">
            {conflicto.pedidosInvolucrados.map((pid) => (
              <button
                key={pid}
                className="btn-resolver-ganador"
                onClick={() => onResolver(pid)}
              >
                <CheckCircle2 size={16} />
                <span>Declarar Ganador a <strong>{pid}</strong></span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
