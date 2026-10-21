import React from 'react';
import { EstadisticasSnapshot, EstadoSistema } from '../types/simulacion';
import {
  TrendingUp,
  Clock,
  CheckCircle2,
  BarChart3,
  Activity,
  Layers
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  LineChart,
  Line,
  CartesianGrid
} from 'recharts';

interface Props {
  estadisticas: EstadisticasSnapshot | undefined;
  recursos?: EstadoSistema['recursos'];
}

export const PanelEstadisticas: React.FC<Props> = ({ estadisticas, recursos }) => {
  if (!estadisticas) {
    return (
      <div className="card-panel">
        <h2 className="panel-titulo">Métricas y Telemetría</h2>
        <div className="cargando-vacio">Cargando estadísticas del sistema...</div>
      </div>
    );
  }

  // Datos para gráfico de cumplimiento SLA por nivel
  const datosSLA = (estadisticas.metricasPorNivel || []).map((m) => ({
    nombre: `L${m.nivel} ${m.nombreNivel}`,
    cumplimiento: Math.round(m.porcentajeCumplimientoSLA),
    completados: m.completados,
  }));

  // Datos para gráfico de tiempos por nivel
  const datosTiempos = (estadisticas.metricasPorNivel || []).map((m) => ({
    nombre: `L${m.nivel} ${m.nombreNivel}`,
    esperaPromedio: Math.round(m.esperaPromedioSegundos),
    procesamientoPromedio: Math.round(m.procesamientoPromedioSegundos),
  }));

  // Datos para serie histórica de cola
  const datosHistoricos = (estadisticas.serieHistoricaCola || []).map((s) => ({
    tiempo: `${Math.round(s.instanteSimulado)}s`,
    enCola: s.pedidosEnCola,
    enProceso: s.pedidosEnProceso,
  }));

  return (
    <div className="card-panel">
      {/* Encabezado */}
      <div className="panel-header">
        <div>
          <h2 className="panel-titulo">
            <BarChart3 size={18} className="icono-panel" />
            <span>Métricas de Rendimiento y Telemetría (SLA)</span>
          </h2>
          <span className="panel-subinfo">
            Contadores atómicos libres de bloqueo (LongAdder y AtomicLong)
          </span>
        </div>
      </div>

      {/* Grid de KPIs principales */}
      <div className="kpis-grid">
        <div className="tarjeta-kpi">
          <div className="kpi-icono kpi-azul">
            <TrendingUp size={20} />
          </div>
          <div className="kpi-contenido">
            <span className="kpi-titulo">Throughput Simulado</span>
            <span className="kpi-numero">
              {estadisticas.throughputPedidosPorMinutoSim.toFixed(2)}
              <small className="kpi-unidad">ped/min sim</small>
            </span>
          </div>
        </div>

        <div className="tarjeta-kpi">
          <div className="kpi-icono kpi-verde">
            <CheckCircle2 size={20} />
          </div>
          <div className="kpi-contenido">
            <span className="kpi-titulo">Cumplimiento SLA Global</span>
            <span className="kpi-numero">
              {estadisticas.porcentajeCumplimientoSLAGlobal.toFixed(1)}%
            </span>
            <small className="kpi-subtexto">
              {estadisticas.pedidosCompletados} de {estadisticas.pedidosCreados} completados
            </small>
          </div>
        </div>

        <div className="tarjeta-kpi">
          <div className="kpi-icono kpi-dorado">
            <Clock size={20} />
          </div>
          <div className="kpi-contenido">
            <span className="kpi-titulo">Espera Media en Cola</span>
            <span className="kpi-numero">
              {estadisticas.esperaPromedioGlobalSegundos.toFixed(1)}
              <small className="kpi-unidad">seg</small>
            </span>
          </div>
        </div>

        <div className="tarjeta-kpi">
          <div className="kpi-icono kpi-purpura">
            <Activity size={20} />
          </div>
          <div className="kpi-contenido">
            <span className="kpi-titulo">Procesamiento Medio</span>
            <span className="kpi-numero">
              {estadisticas.procesamientoPromedioGlobalSegundos.toFixed(1)}
              <small className="kpi-unidad">seg</small>
            </span>
          </div>
        </div>

        <div className="tarjeta-kpi">
          <div className="kpi-icono kpi-naranja">
            <Layers size={20} />
          </div>
          <div className="kpi-contenido">
            <span className="kpi-titulo">Reencolados por Contención</span>
            <span className="kpi-numero">{estadisticas.pedidosReencolados}</span>
            <small className="kpi-subtexto">Retries sin inanición</small>
          </div>
        </div>
      </div>

      {/* Gráficos Recharts */}
      <div className="graficos-grid">
        {/* Gráfico 1: Cumplimiento SLA */}
        <div className="grafico-card">
          <h3 className="grafico-titulo">% Cumplimiento SLA por Nivel de Servicio</h3>
          <div className="grafico-contenedor">
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={datosSLA}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="nombre" stroke="#94a3b8" />
                <YAxis domain={[0, 100]} stroke="#94a3b8" unit="%" />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', borderColor: '#475569', color: '#f8fafc' }}
                />
                <Bar dataKey="cumplimiento" fill="#10b981" name="% SLA Cumplido" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Gráfico 2: Tiempos de Espera y Procesamiento */}
        <div className="grafico-card">
          <h3 className="grafico-titulo">Tiempos Promedio (Espera vs Procesamiento) por Nivel</h3>
          <div className="grafico-contenedor">
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={datosTiempos}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="nombre" stroke="#94a3b8" />
                <YAxis stroke="#94a3b8" unit="s" />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', borderColor: '#475569', color: '#f8fafc' }}
                />
                <Legend />
                <Bar dataKey="esperaPromedio" fill="#f59e0b" name="Espera (s)" radius={[4, 4, 0, 0]} />
                <Bar dataKey="procesamientoPromedio" fill="#38bdf8" name="Procesamiento (s)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Gráfico 3: Serie Histórica de Colas */}
        <div className="grafico-card grafico-ancho-completo">
          <h3 className="grafico-titulo">Evolución Dinámica: Ocupación de Cola vs Pedidos en Ejecución</h3>
          <div className="grafico-contenedor">
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={datosHistoricos}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="tiempo" stroke="#94a3b8" />
                <YAxis stroke="#94a3b8" />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', borderColor: '#475569', color: '#f8fafc' }}
                />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="enCola"
                  stroke="#ef4444"
                  name="En Cola de Espera"
                  strokeWidth={2}
                  dot={false}
                />
                <Line
                  type="monotone"
                  dataKey="enProceso"
                  stroke="#3b82f6"
                  name="En Ejecución Activa"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      <div className="grafico-card">
        <h3 className="grafico-titulo">Ocupación instantánea de recursos (%)</h3>
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={Object.values(recursos || {}).map(p => ({ nombre: p.tipo, ocupacion: 100 * p.unidades.filter(u => u.duenoActual && !u.enMantenimiento).length / p.capacidadTotal }))}>
            <XAxis dataKey="nombre" stroke="#94a3b8" /><YAxis domain={[0, 100]} stroke="#94a3b8" />
            <Tooltip /><Bar dataKey="ocupacion" fill="#38bdf8" name="Ocupación %" />
          </BarChart>
        </ResponsiveContainer>
      </div>
      {/* Tabla Desglosada por Niveles */}
      <div className="metricas-tabla-seccion">
        <h3 className="subtitulo-seccion">Desglose Detallado por Acuerdo de Nivel de Servicio (SLA)</h3>
        <div className="tabla-responsive">
          <table className="tabla-redxela tabla-compacta">
            <thead>
              <tr>
                <th>Nivel</th>
                <th>Nombre del Servicio</th>
                <th>Completados</th>
                <th>Cumplieron SLA</th>
                <th>Fallaron SLA</th>
                <th>% Cumplimiento</th>
                <th>Espera Promedio</th>
                <th>Espera Máxima</th>
              </tr>
            </thead>
            <tbody>
              {estadisticas.metricasPorNivel.map((m) => (
                <tr key={m.nivel}>
                  <td><strong>L{m.nivel}</strong></td>
                  <td>{m.nombreNivel}</td>
                  <td>{m.completados}</td>
                  <td className="texto-verde">{m.cumplieronSLA}</td>
                  <td className={m.fallaronSLA > 0 ? 'texto-rojo' : 'texto-muted'}>{m.fallaronSLA}</td>
                  <td>
                    <span className={`badge-porcentaje ${m.porcentajeCumplimientoSLA >= 90 ? 'optimo' : 'regular'}`}>
                      {m.porcentajeCumplimientoSLA.toFixed(1)}%
                    </span>
                  </td>
                  <td>{m.esperaPromedioSegundos.toFixed(1)}s</td>
                  <td>{m.esperaMaximaSegundos.toFixed(1)}s</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
