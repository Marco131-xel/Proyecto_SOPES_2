import { FormEvent, useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../services/api';
import { EstadoComparacion, TipoRecurso } from '../types/simulacion';

const POLITICAS: Record<string, { nombre: string; color: string }> = {
  PRIORIDAD_ESTRICTA: { nombre: 'Prioridad estricta', color: '#38bdf8' },
  PRIORIDAD_CON_AGING: { nombre: 'Prioridad con aging', color: '#a78bfa' },
  MENOR_CANTIDAD_PRIMERO: { nombre: 'SJF', color: '#f59e0b' },
  EDF: { nombre: 'EDF', color: '#10b981' },
};

/** Consulta independiente de SSE: navegar entre pestañas conserva el trabajo en el servidor. */
export function PanelComparacion() {
  const [semilla, setSemilla] = useState('42');
  const [cantidad, setCantidad] = useState(300);
  const [tasa, setTasa] = useState(4);
  const [trabajo, setTrabajo] = useState<EstadoComparacion | null>(null);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [revision, setRevision] = useState(0);
  const [metrica, setMetrica] = useState<'esperaPromedioSegundos' | 'porcentajeCumplimientoSLA'>('esperaPromedioSegundos');

  useEffect(() => {
    const abortador = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let cerrado = false;
    const consultar = async () => {
      try {
        const estado = await api.obtenerComparacion(abortador.signal);
        if (cerrado) return;
        setTrabajo(estado);
        if (estado.estado === 'EJECUTANDO') timer = setTimeout(consultar, 500);
      } catch (e) {
        if (!cerrado) setError(e instanceof Error ? e.message : 'No se pudo consultar la comparación');
      }
    };
    void consultar();
    return () => { cerrado = true; clearTimeout(timer); abortador.abort(); };
  }, [revision]);

  const ejecutar = async (event: FormEvent) => {
    event.preventDefault();
    if (!semilla.trim() || !Number.isSafeInteger(Number(semilla))) {
      setError('La semilla debe ser un entero representable con precisión en el navegador');
      return;
    }
    setError('');
    setEnviando(true);
    try {
      setTrabajo(await api.ejecutarComparacion({ semilla: Number(semilla), cantidadPedidos: cantidad, tasaLlegadasPorMinuto: tasa }));
      setRevision(r => r + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo iniciar la comparación');
    } finally { setEnviando(false); }
  };

  const ocupado = enviando || trabajo?.estado === 'EJECUTANDO';
  const resultado = trabajo?.resultado;
  const datosGrafica = [1, 2, 3, 4, 5].map(nivel => {
    const valores: Record<string, string | number | null> = { nivel: `Nivel ${nivel}` };
    resultado?.politicas.forEach(p => {
      const muestra = p.niveles.find(n => n.nivel === nivel);
      valores[p.politica] = muestra && muestra.completados > 0 ? muestra[metrica] : null;
    });
    return valores;
  });
  const fmt = (n: number) => n.toLocaleString('es-GT', { maximumFractionDigits: 2 });

  return <section className="card-panel">
    <div className="panel-header"><h2 className="panel-titulo">Comparador de políticas</h2></div>
    <p>Ejecuta la misma carga para las cuatro políticas con recursos independientes. El sistema en vivo continúa funcionando.</p>
    <form onSubmit={ejecutar} className="comparacion-form">
      <label>Semilla<input className="input-redxela" type="number" step="1" required value={semilla} onChange={e => setSemilla(e.target.value)} /></label>
      <label>Pedidos<input className="input-redxela" type="number" min="1" max="1000" step="1" required value={cantidad} onChange={e => setCantidad(Number(e.target.value))} /></label>
      <label>Llegadas / min sim<input className="input-redxela" type="number" min="0.1" max="120" step="0.1" required value={tasa} onChange={e => setTasa(Number(e.target.value))} /></label>
      <button type="submit" className="btn-primario" disabled={ocupado}>{ocupado ? 'Comparando…' : 'Comparar políticas'}</button>
      <button type="button" className="btn-secundario-sm" onClick={() => { setError(''); setRevision(r => r + 1); }}>Consultar estado</button>
    </form>
    {error && <p className="mensaje-feedback error" role="alert">{error}</p>}
    {trabajo?.estado === 'ERROR' && <p className="mensaje-feedback error" role="alert">{trabajo.error}</p>}
    <p role="status">{ocupado ? 'Comparación en ejecución. Puede cambiar de pestaña y consultar el resultado después.' : trabajo?.estado === 'SIN_EJECUTAR' ? 'Aún no se ha ejecutado una comparación.' : trabajo?.estado === 'ERROR' ? 'La comparación falló. Puede volver a intentarlo.' : resultado ? 'Comparación completada.' : 'Consultando estado…'}</p>
    {resultado && <>
      <p className="panel-subinfo">Semilla {resultado.configuracion.semilla} · {resultado.configuracion.cantidadPedidos} pedidos · {resultado.configuracion.tasaLlegadasPorMinuto} llegadas/min sim</p>
      <p className="comparacion-huella">Huella de la carga: <code>{resultado.huellaCarga}</code></p>
      <p>{resultado.alcance}</p>
      <div className="tabla-responsive"><table className="tabla-redxela tabla-compacta">
        <thead><tr><th>Política</th><th>Completados</th><th>Espera media (s)</th><th>Espera máxima (s)</th><th>SLA (%)</th><th>Pedidos/min sim</th><th>Ocupación (%)</th><th>Espera &gt; 2× SLA</th><th>Horizonte (s)</th></tr></thead>
        <tbody>{resultado.politicas.map(p => <tr key={p.politica}>
          <td title={p.descripcion}>{POLITICAS[p.politica]?.nombre ?? p.politica}</td><td>{p.pedidosCompletados}</td>
          <td>{fmt(p.esperaPromedioSegundos)}</td><td>{fmt(p.esperaMaximaSegundos)}</td><td>{fmt(p.porcentajeSLA)}</td>
          <td>{fmt(p.throughputPedidosPorMinuto)}</td><td>{fmt(p.ocupacionMediaPorcentaje)}</td><td>{p.pedidosConInanicion}</td><td>{p.horizonteSimuladoSegundos}</td>
        </tr>)}</tbody>
      </table></div>
      <div className="grafico-card">
        <label htmlFor="metrica-comparacion">Comparar por nivel: </label>
        <select id="metrica-comparacion" className="select-redxela" value={metrica} onChange={e => setMetrica(e.target.value as typeof metrica)}>
          <option value="esperaPromedioSegundos">Espera media (segundos)</option><option value="porcentajeCumplimientoSLA">Cumplimiento SLA (%)</option>
        </select>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={datosGrafica}><CartesianGrid strokeDasharray="3 3" stroke="#334155" />
            <XAxis dataKey="nivel" stroke="#94a3b8" /><YAxis stroke="#94a3b8" domain={metrica === 'porcentajeCumplimientoSLA' ? [0, 100] : [0, 'auto']} />
            <Tooltip contentStyle={{ backgroundColor: '#1e293b', borderColor: '#475569', color: '#f8fafc' }} /><Legend />
            {resultado.politicas.map(p => <Bar key={p.politica} dataKey={p.politica} name={POLITICAS[p.politica]?.nombre ?? p.politica} fill={POLITICAS[p.politica]?.color ?? '#94a3b8'} />)}
          </BarChart>
        </ResponsiveContainer>
      </div>
      <details><summary>Desglose de espera y SLA por nivel</summary><div className="tabla-responsive"><table className="tabla-redxela tabla-compacta">
        <thead><tr><th>Política</th><th>Nivel</th><th>Completados</th><th>Espera media (s)</th><th>Espera máxima (s)</th><th>SLA (%)</th></tr></thead>
        <tbody>{resultado.politicas.flatMap(p => p.niveles.map(n => <tr key={`${p.politica}-${n.nivel}`}>
          <td>{POLITICAS[p.politica]?.nombre ?? p.politica}</td><td>{n.nivel} — {n.nombreNivel}</td><td>{n.completados}</td><td>{fmt(n.esperaPromedioSegundos)}</td><td>{n.esperaMaximaSegundos}</td><td>{n.completados ? fmt(n.porcentajeCumplimientoSLA) : 'Sin muestras'}</td>
        </tr>))}</tbody>
      </table></div></details>
      <details><summary>Ocupación acumulada por recurso (%)</summary><div className="tabla-responsive"><table className="tabla-redxela tabla-compacta">
        <thead><tr><th>Recurso</th>{resultado.politicas.map(p => <th key={p.politica}>{POLITICAS[p.politica]?.nombre ?? p.politica}</th>)}</tr></thead>
        <tbody>{(Object.keys(resultado.politicas[0]?.ocupacionPorRecurso ?? {}) as TipoRecurso[]).sort().map(tipo => <tr key={tipo}><td>{tipo}</td>{resultado.politicas.map(p => <td key={p.politica}>{fmt(p.ocupacionPorRecurso[tipo])}</td>)}</tr>)}</tbody>
      </table></div></details>
      <p className="comparacion-conclusion">{resultado.conclusion}</p>
    </>}
  </section>;
}
