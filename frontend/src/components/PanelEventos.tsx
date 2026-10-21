import { Evento } from '../types/simulacion';

/** Muestra eventos del servidor: no infiere transiciones a partir de fotos SSE. */
export function PanelEventos({ eventos = [] }: { eventos?: Evento[] }) {
  return <section className="card-panel panel-eventos">
    <h2 className="panel-titulo">Registro de eventos</h2>
    <div className="eventos-lista">
      {eventos.length === 0 && <p>Aún no hay eventos.</p>}
      {eventos.map(e => <div key={e.id} className="evento-fila">
        <time>{e.instanteSimulado}s</time><strong>{e.tipo}</strong><span>{e.mensaje}</span>
      </div>)}
    </div>
  </section>;
}
