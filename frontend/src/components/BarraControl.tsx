import React, { useEffect, useState } from 'react';
import {
  EstadoSistema
} from '../types/simulacion';
import {
  Play,
  Pause,
  Clock,
  Zap,
  ShieldAlert,
  ShieldCheck,
  AlertTriangle,
  Sliders,
  Radio
} from 'lucide-react';

interface Props {
  estado: EstadoSistema | null;
  conectado: boolean;
  modoConexion: 'sse' | 'polling' | 'reconectando';
  onCambioVelocidad: (factor: number) => void;
  onPausarReanudar: (pausar: boolean) => void;
  onCambioPolitica: (politica: string) => void;
  onTogglePrevencion: (activar: boolean) => void;
  onToggleGenerador: (iniciar: boolean) => void;
  onConfigurarGenerador: (tasa: number, semilla?: number) => void;
  onProvocarDeadlock: () => void;
}

export const BarraControl: React.FC<Props> = ({
  estado,
  conectado,
  modoConexion,
  onCambioVelocidad,
  onPausarReanudar,
  onCambioPolitica,
  onTogglePrevencion,
  onToggleGenerador,
  onConfigurarGenerador,
  onProvocarDeadlock,
}) => {
  const [tasaLocal, setTasaLocal] = useState<number>(4);
  const [semillaLocal, setSemillaLocal] = useState('');
  useEffect(() => { if (estado) setTasaLocal(estado.tasaGenerador); }, [estado?.tasaGenerador]);
  const [mostrarConfigGen, setMostrarConfigGen] = useState(false);

  const formatoTiempo = (segundosTotal: number) => {
    const d = Math.floor(segundosTotal / 86400);
    const h = Math.floor((segundosTotal % 86400) / 3600);
    const m = Math.floor((segundosTotal % 3600) / 60);
    const s = Math.floor(segundosTotal % 60);

    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d > 0 ? `Día ${d + 1} ` : ''}${pad(h)}:${pad(m)}:${pad(s)}`;
  };

  const velocidades = [1, 60, 120, 300, 600];

  const politicas = [
    { id: 'PRIORIDAD_ESTRICTA', label: 'Prioridad Estricta' },
    { id: 'PRIORIDAD_CON_AGING', label: 'Prioridad con Aging' },
    { id: 'MENOR_CANTIDAD_PRIMERO', label: 'Menor Cantidad (SJF)' },
    { id: 'EDF', label: 'Earliest Deadline First (EDF)' },
  ];

  const factorActual = estado?.factorReloj ?? 60;
  const pausado = estado?.relojPausado ?? false;
  const generadorActivo = estado?.generadorActivo && !estado?.generadorPausado;
  const prevencion = estado?.prevencionDeadlock ?? false;
  const hayMantenimiento = Object.values(estado?.recursos ?? {}).some(pool => pool.unidades.some(u => u.enMantenimiento));

  return (
    <header className="barra-control">
      <div className="barra-superior">
        {/* Marca y Estado de Conexión */}
        <div className="logo-seccion">
          <div className="logo-icono">
            <Radio className={conectado ? 'icono-vivo activo' : 'icono-vivo'} size={20} />
          </div>
          <div>
            <h1 className="logo-titulo">REDXela</h1>
            <span className="logo-subtitulo">
              {conectado ? (
                <span className="badge badge-online">● Telemetría SSE en Vivo</span>
              ) : (
                <span className="badge badge-offline">{modoConexion === 'polling' ? '◐ Sondeo activo / reconectando SSE' : '○ Reconectando…'}</span>
              )}
            </span>
          </div>
        </div>

        {/* Reloj Simulado */}
        <div className="reloj-card">
          <Clock size={18} className="icono-dorado" />
          <div className="reloj-info">
            <span className="reloj-etiqueta">Tiempo Simulado</span>
            <span className="reloj-digitos">
              {formatoTiempo(estado?.tiempoSimuladoSegundos ?? 0)}
            </span>
          </div>
          <button
            className={`btn-icon ${pausado ? 'btn-pausado' : 'btn-reanudado'}`}
            title={pausado ? 'Reanudar Reloj' : 'Pausar Reloj'}
            onClick={() => onPausarReanudar(!pausado)}
          >
            {pausado ? <Play size={16} /> : <Pause size={16} />}
          </button>
        </div>

        {/* Selectores de Velocidad */}
        <div className="grupo-velocidad">
          <span className="grupo-etiqueta">Velocidad:</span>
          <div className="btn-group">
            {velocidades.map((v) => (
              <button
                key={v}
                className={`btn-tag ${factorActual === v ? 'activo' : ''}`}
                onClick={() => onCambioVelocidad(v)}
              >
                {v}x
              </button>
            ))}
          </div>
        </div>

        {/* Selector de Política */}
        <div className="grupo-politica">
          <label htmlFor="politica-select" className="grupo-etiqueta">
            Política:
          </label>
          <select
            id="politica-select"
            className="select-politica"
            value={estado?.politicaActiva ?? 'PRIORIDAD_ESTRICTA'}
            onChange={(e) => onCambioPolitica(e.target.value)}
          >
            {politicas.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label}
              </option>
            ))}
          </select>
        </div>

        {/* Toggle Prevención Deadlock */}
        <div className="grupo-prevencion">
          <button
            className={`btn-toggle ${prevencion ? 'btn-seguro' : 'btn-riesgo'}`}
            title="Alternar entre Prevención All-or-Nothing y Asignación Incremental con Detección"
            onClick={() => onTogglePrevencion(!prevencion)}
          >
            {prevencion ? (
              <>
                <ShieldCheck size={16} />
                <span>Prevención Activa (All-or-Nothing)</span>
              </>
            ) : (
              <>
                <ShieldAlert size={16} />
                <span>Modo Detección (Incremental)</span>
              </>
            )}
          </button>
        </div>

        {prevencion && hayMantenimiento && !estado?.conflicto.hayConflicto && (
          <button className="btn-toggle btn-seguro" onClick={() => onTogglePrevencion(true)}>
            Restaurar recursos
          </button>
        )}

        {/* Generador de Tráfico */}
        <div className="grupo-generador">
          <button
            className={`btn-toggle ${generadorActivo ? 'btn-activo' : 'btn-inactivo'}`}
            onClick={() => onToggleGenerador(!generadorActivo)}
            title={generadorActivo ? 'Pausar generador de pedidos' : 'Iniciar generador de pedidos'}
          >
            <Zap size={16} />
            <span>Generador: {generadorActivo ? 'ON' : 'OFF'}</span>
          </button>
          <button
            className="btn-icon-subtle"
            title="Configurar Tasa del Generador"
            onClick={() => setMostrarConfigGen(!mostrarConfigGen)}
          >
            <Sliders size={16} />
          </button>
        </div>

        {/* Botón de Provocar Deadlock */}
        <div className="grupo-deadlock-btn">
          <button
            className="btn-peligro"
            onClick={onProvocarDeadlock}
            title="Inyecta deliberadamente 2 pedidos cruzados sin prevención para forzar interbloqueo"
          >
            <AlertTriangle size={16} />
            <span>Provocar Deadlock</span>
          </button>
        </div>
      </div>

      {/* Subbarra opcional de configuración del Generador */}
      {mostrarConfigGen && (
        <div className="subbarra-config">
          <div className="config-item">
            <span>Tasa de Pedidos: <strong>{tasaLocal}</strong> ped/min sim</span>
            <input
              type="range"
              min="1"
              max="20"
              value={tasaLocal}
              onChange={(e) => setTasaLocal(Number(e.target.value))}
            />
            <label>Semilla (opcional)
              <input type="number" step="1" value={semillaLocal} className="input-redxela" onChange={e => setSemillaLocal(e.target.value)} />
            </label>
            <button
              disabled={!!semillaLocal.trim() && !Number.isSafeInteger(Number(semillaLocal))}
              className="btn-primario-sm"
              onClick={() => onConfigurarGenerador(tasaLocal, semillaLocal.trim() ? Number(semillaLocal) : undefined)}
            >
              Aplicar configuración
            </button>
          </div>
        </div>
      )}
    </header>
  );
};
