# Guion de demostración y ensayo — Fase 9

Estado actual: **ambos recorridos visuales pendientes**. La preparación automatizada no cuenta como ensayo de pantalla. Usar 1440×900 o la resolución real del proyector y comprobar consola del navegador y terminal backend.

## Preparación de cada recorrido

Iniciar una sesión nueva de backend y frontend. Esperar que los 18 productos tengan stock inicial, confirmar SSE y prevención activa. Mantener el comparador con configuración 42/300/4 para poder repetir la misma evidencia. No inventar datos en la consola del navegador.

## Pasos y resultados esperados

| Paso | Acción | Evidencia de éxito |
|---|---|---|
| 1 | Generador a tasa baja, mostrar recursos y pedidos | Progreso real, dueño por unidad y completados en eventos |
| 2 | Subir tasa, observar cola y cambiar políticas | Leyenda y orden cambian; los ya activos siguen su trabajo |
| 3 | Crear cliente Exprés y pedido manual | Hereda nivel 1; con prioridad estricta se evalúa antes que niveles inferiores factibles, sin expropiar activos |
| 4 | Crear pedido sin stock, recibir su producto | Camión, actualización y reactivación automática sin volver a registrar el pedido |
| 5 | Observar almacén y recibir un lote que aproveche huecos | Ubicaciones indexadas, celdas separadas y stock coherente |
| 6 | Registrar suficientes camiones para presión de cola | Consumidor procesa más lento; cola llena detiene recepción sin descartar |
| 7 | Pausar generador y terminar la cola con reabastecimiento | Sin pedidos activos ni pendientes antes del escenario |
| 8 | Provocar deadlock con stock PES-01≥2 y VOL-01≥1 | Dos pedidos bloqueados, grafo y explicación de recursos retenidos/solicitados |
| 9 | Elegir ganador, alternarlo entre recorridos | Víctima vuelve a espera; ambos completan; Restaurar recursos devuelve permisos |
| 10 | Comparar 42/300/4, cambiar de pestaña y volver | Cuatro resultados y huella 57015b22436e54fbf00ca3f3274ccd8f7c6d7983fec90bbd92f751c5d7701844 |
| 11 | Repetir comparación y revisar métricas por nivel | Misma huella y valores; conclusión no implica una política universalmente óptima |
| 12 | Interrumpir SSE y recuperar conexión | Sondeo o aviso visible; reconexión sin duplicar consultas |
| 13 | Revisar legibilidad y capturas | No hay solapamientos, textos ilegibles ni errores inesperados de consola |

Si se acumulan demasiados pedidos sin stock, pausar y reabastecer. Para la escena aislada se puede reiniciar el backend, indicando expresamente que empieza una sesión nueva; no presentar ese reinicio como recuperación de pedidos anteriores.

## Registro de ensayos

| Campo | Recorrido 1 | Recorrido 2 |
|---|---|---|
| Fecha y responsable | Pendiente | Pendiente |
| Commit / hash del paquete | Pendiente | Pendiente |
| Resolución / navegador | Pendiente | Pendiente |
| Pasos 1–6 | Pendiente | Pendiente |
| Deadlock y ganador elegido | Pendiente | Pendiente |
| Comparación y repetición | Pendiente | Pendiente |
| Reconexión | Pendiente | Pendiente |
| Errores y correcciones | Pendiente | Pendiente |
| Resultado final | No ejecutado | No ejecutado |

## Capturas y defensa

Guardar capturas reales según `docs/capturas/README.md`, incluyendo operaciones, gestión manual, almacén, recepción, conflicto, resolución, métricas, comparación y reconexión. Para Playwright, guardar primero los artefactos de trabajo en `output/playwright/`, comprobarlos y copiar las imágenes elegidas a docs/capturas.

Antes de la defensa, repasar sección 12 del Manual Técnico. Explicar Coffman con matrices, diferencia entre prevención y recuperación, productor-consumidor y contrapresión, reserva de stock, asignación indexada, aging, limitaciones de SJF/EDF y por qué el comparador usa reloj lógico e instancias aisladas.
