import React, { useState } from 'react';
import { AlmacenSnapshot } from '../types/simulacion';
import { Layers, Info, Package } from 'lucide-react';

interface Props {
  almacen: AlmacenSnapshot | undefined;
}

export const PanelAlmacen: React.FC<Props> = ({ almacen }) => {
  const [pasilloActivo, setPasilloActivo] = useState<number>(1);
  const [celdaSeleccionada, setCeldaSeleccionada] = useState<string | null>(null);

  if (!almacen) {
    return (
      <div className="card-panel">
        <h2 className="panel-titulo">Almacén Indexado y Mapa de Memoria</h2>
        <div className="cargando-vacio">Cargando estado del almacén...</div>
      </div>
    );
  }

  const celdaActual = almacen.ubicaciones.find(u => u.id === celdaSeleccionada);

  // Filtrar celdas del pasillo activo (4 niveles x 10 posiciones = 40 celdas)
  const celdasPasillo = almacen.ubicaciones.filter((u) => u.pasillo === pasilloActivo);

  // Organizar por niveles (del nivel 4 arriba al nivel 1 abajo como estantes físicos)
  const niveles = [4, 3, 2, 1];

  const getColorMercancia = (tipo: string | null, libre: boolean) => {
    if (libre || !tipo) return 'celda-libre';
    switch (tipo) {
      case 'PERECEDERO': return 'mercancia-perecedero';
      case 'FRAGIL': return 'mercancia-fragil';
      case 'ELECTRONICO': return 'mercancia-refrigerado';
      case 'VOLUMINOSO': return 'mercancia-valioso';
      case 'PESADO': return 'mercancia-pesado';
      case 'GENERAL': return 'mercancia-estandar';
      default: return 'mercancia-estandar';
    }
  };

  return (
    <div className="card-panel">
      {/* Encabezado e Indicadores Globales */}
      <div className="panel-header">
        <div className="almacen-header-titulos">
          <h2 className="panel-titulo">
            <Layers size={18} className="icono-panel" />
            <span>Almacén Físico (Asignación No Contigua Indexada)</span>
          </h2>
          <span className="panel-subinfo">
            BitSet de Bloques Libres (First-Fit) + Inodos/Lotes con Punteros de Asignación
          </span>
        </div>

        <div className="almacen-kpis">
          <div className="kpi-mini">
            <span className="kpi-valor">
              {almacen.ubicacionesOcupadas} / {almacen.totalUbicaciones}
            </span>
            <span className="kpi-etiqueta">Ocupadas ({almacen.porcentajeOcupacion}%)</span>
          </div>
          <div className="kpi-mini">
            <span className="kpi-valor">{almacen.fragmentacionInternaTotalUnidades} uds</span>
            <span className="kpi-etiqueta">Frag. Interna</span>
          </div>
          {almacen.lotesPendientesEspacio > 0 && (
            <div className="kpi-mini alerta">
              <span className="kpi-valor">{almacen.lotesPendientesEspacio}</span>
              <span className="kpi-etiqueta">Lotes Bloqueados</span>
            </div>
          )}
        </div>
      </div>

      {/* Selector de Pasillos */}
      <div className="pasillos-tabs">
        <span className="tabs-etiqueta">Pasillos Físicos:</span>
        {[1, 2, 3, 4, 5, 6].map((p) => {
          const ocupadasEnP = almacen.ubicaciones.filter((u) => u.pasillo === p && !u.libre).length;
          return (
            <button
              key={p}
              className={`tab-pasillo ${pasilloActivo === p ? 'activo' : ''}`}
              onClick={() => setPasilloActivo(p)}
            >
              <span>Pasillo {p}</span>
              <span className="badge-cuenta">{ocupadasEnP}/40</span>
            </button>
          );
        })}
      </div>

      {/* Matriz del Pasillo (Heatmap de Celdas) */}
      <div className="estanteria-contenedor">
        <div className="estanteria-header">
          <span>Vista de Estantería: Pasillo #{pasilloActivo} (4 Niveles × 10 Posiciones = 40 Celdas)</span>
          <div className="leyenda-mercancias">
            <span className="leyenda-item"><i className="dot mercancia-perecedero" />Perecedero</span>
            <span className="leyenda-item"><i className="dot mercancia-fragil" />Frágil</span>
            <span className="leyenda-item"><i className="dot mercancia-refrigerado" />Electrónico</span>
            <span className="leyenda-item"><i className="dot mercancia-valioso" />Voluminoso</span>
            <span className="leyenda-item"><i className="dot mercancia-pesado" />Pesado</span>
            <span className="leyenda-item"><i className="dot mercancia-estandar" />General</span>
            <span className="leyenda-item"><i className="dot celda-libre" />Libre</span>
          </div>
        </div>

        <div className="matriz-estanteria">
          {niveles.map((nivel) => {
            const celdasNivel = celdasPasillo
              .filter((u) => u.nivel === nivel)
              .sort((a, b) => a.posicion - b.posicion);

            return (
              <div key={nivel} className="estanteria-fila">
                <div className="nivel-indicador">Nivel {nivel}</div>
                <div className="celdas-fila">
                  {celdasNivel.map((celda) => {
                    const colorClase = getColorMercancia(celda.tipoMercancia, celda.libre);
                    const seleccionada = celdaSeleccionada === celda.id;

                    return (
                      <div
                        key={celda.id}
                        className={`celda-slot ${colorClase} ${seleccionada ? 'seleccionada' : ''}`}
                        onClick={() => setCeldaSeleccionada(celda.id)}
                        title={`Celda: ${celda.id} | ${celda.libre ? 'LIBRE' : `${celda.productoNombre} (${celda.cantidadOcupada}/${celda.capacidadMaxima})`}`}
                      >
                        <span className="pos-numero">{celda.posicion}</span>
                        {!celda.libre && (
                          <span className="ocupacion-texto">
                            {celda.cantidadOcupada}u
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Inspector de Celda Seleccionada */}
      {celdaActual && (
        <div className="inspector-celda">
          <div className="inspector-header">
            <Info size={16} />
            <span>Detalle de Ubicación Seleccionada: <strong>{celdaActual.id}</strong></span>
            <button className="btn-cerrar-mini" onClick={() => setCeldaSeleccionada(null)}>×</button>
          </div>
          <div className="inspector-grid">
            <div><strong>Índice de Bloque BitSet:</strong> #{celdaActual.indiceLineal}</div>
            <div><strong>Estado:</strong> {celdaActual.libre ? 'LIBRE' : 'OCUPADO'}</div>
            <div><strong>Producto:</strong> {celdaActual.productoNombre ?? 'Sin producto asignado'}</div>
            <div><strong>Tipo de Mercancía:</strong> {celdaActual.tipoMercancia ?? 'N/A'}</div>
            <div><strong>Cantidad Almacenada:</strong> {celdaActual.cantidadOcupada} / {celdaActual.capacidadMaxima} uds</div>
            <div><strong>Lote Asignado:</strong> {celdaActual.loteId ?? 'N/A'}</div>
          </div>
        </div>
      )}

      {/* Tabla de Lotes Activos (Asignación No Contigua) */}
      <div className="lotes-seccion">
        <h3 className="subtitulo-seccion">
          <Package size={16} />
          <span>Tabla de Asignación Indexada (Lotes Activos y sus Punteros de Bloque)</span>
        </h3>

        {almacen.lotesActivos.length === 0 ? (
          <div className="texto-muted">No hay lotes de inventario activos en este momento.</div>
        ) : (
          <div className="tabla-responsive">
            <table className="tabla-redxela tabla-compacta">
              <thead>
                <tr>
                  <th>Lote ID</th>
                  <th>Producto</th>
                  <th>Total Uds</th>
                  <th>Ubicaciones Asignadas (Punteros No Contiguos)</th>
                  <th>Frag. Interna</th>
                </tr>
              </thead>
              <tbody>
                {almacen.lotesActivos.map((lote) => (
                  <tr key={lote.id}>
                    <td><code>{lote.id}</code></td>
                    <td>{lote.productoNombre}</td>
                    <td>{lote.cantidadTotal} uds</td>
                    <td>
                      <div className="punteros-ubicaciones">
                        {lote.tablaAsignacion.map((p, idx) => (
                          <span key={idx} className="chip-puntero">
                            {p.ubicacionId} ({p.unidades}u)
                          </span>
                        ))}
                      </div>
                    </td>
                    <td>
                      {lote.fragmentacionInternaUnidades > 0 ? (
                        <span className="frag-aviso">{lote.fragmentacionInternaUnidades} uds desaprovechadas</span>
                      ) : (
                        <span className="texto-verde">0 (Óptimo)</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
