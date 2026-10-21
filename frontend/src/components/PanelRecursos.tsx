import React from 'react';
import { PoolSnapshot, TipoRecurso } from '../types/simulacion';
import {
  Users,
  Box,
  Truck,
  Forklift,
  CheckCircle,
  Scan,
  Cpu
} from 'lucide-react';

interface Props {
  recursos: Record<TipoRecurso, PoolSnapshot> | undefined;
}

const NOMBRES_RECURSOS: Record<TipoRecurso, { nombre: string; icon: React.ReactNode }> = {
  ENCARGADO: { nombre: 'Encargados de Despacho', icon: <Users size={18} /> },
  EMPAQUE: { nombre: 'Estaciones de Empaque', icon: <Box size={18} /> },
  CARGA: { nombre: 'Bahías de Carga Pesada', icon: <Truck size={18} /> },
  MONTACARGAS: { nombre: 'Montacargas Mecánicos', icon: <Forklift size={18} /> },
  CONTROL_CALIDAD: { nombre: 'Control de Calidad', icon: <CheckCircle size={18} /> },
  ESCANER: { nombre: 'Escáner de Recepción', icon: <Scan size={18} /> },
};

export const PanelRecursos: React.FC<Props> = ({ recursos }) => {
  if (!recursos) {
    return (
      <div className="card-panel">
        <h2 className="panel-titulo">Recursos Compartidos (Semáforos)</h2>
        <div className="cargando-vacio">Cargando estado de recursos...</div>
      </div>
    );
  }

  const tipos = Object.keys(NOMBRES_RECURSOS) as TipoRecurso[];

  return (
    <div className="card-panel">
      <div className="panel-header">
        <h2 className="panel-titulo">
          <Cpu size={18} className="icono-panel" />
          <span>Pools de Recursos Compartidos</span>
        </h2>
        <span className="panel-subinfo">Semáforos Contadores Justos (FIFO)</span>
      </div>

      <div className="recursos-grid">
        {tipos.map((tipo) => {
          const pool = recursos[tipo];
          if (!pool) return null;

          const meta = NOMBRES_RECURSOS[tipo];
          const ocupados = pool.capacidadTotal - pool.disponibles;
          const porcentaje = Math.round((ocupados / pool.capacidadTotal) * 100);

          return (
            <div key={tipo} className="tarjeta-recurso">
              <div className="recurso-header">
                <div className="recurso-icono">{meta.icon}</div>
                <div className="recurso-titulos">
                  <span className="recurso-nombre">{meta.nombre}</span>
                  <span className="recurso-tipo">{tipo}</span>
                </div>
                <span className={`disponibilidad-chip ${pool.disponibles === 0 ? 'agotado' : 'disponible'}`}>
                  {pool.disponibles}/{pool.capacidadTotal} libres
                </span>
              </div>

              {/* Barra de Ocupación */}
              <div className="barra-progreso-fondo">
                <div
                  className={`barra-progreso-relleno ${
                    porcentaje === 100 ? 'lleno' : porcentaje > 50 ? 'medio' : 'bajo'
                  }`}
                  style={{ width: `${porcentaje}%` }}
                />
              </div>

              {/* Lista de Unidades Individuales */}
              <div className="unidades-lista">
                {pool.unidades.map((u) => {
                  let estadoClase = 'libre';
                  let estadoTexto = 'Libre';

                  if (u.enMantenimiento) {
                    estadoClase = 'mantenimiento';
                    estadoTexto = 'Mantenimiento';
                  } else if (u.duenoActual) {
                    estadoClase = 'ocupado';
                    estadoTexto = u.duenoActual;
                  }

                  return (
                    <div key={u.id} className={`unidad-item ${estadoClase}`}>
                      <span className="unidad-nombre">{u.nombreLegible}</span>
                      <span className="unidad-badge">{estadoTexto}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
