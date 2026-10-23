# Manual técnico de REDXela — borrador incremental

Este archivo inicia la sección del comparador entregada en la Fase 7. La arquitectura general, infraestructura, diagramas y demás secciones del manual se completarán en la Fase 8; este documento todavía no es el manual final.

## 7. Comparación experimental de políticas

### Objetivo y alcance

`SimuladorComparativo` mide prioridad estricta, prioridad con aging, menor cantidad primero (SJF) y EDF con la misma carga finita. Cada política recibe pedidos nuevos, su propio `GestorRecursos`, `ListaEspera`, `Estadisticas` y reloj. Reutiliza las implementaciones de las políticas y `TrabajadorPedido` del sistema en vivo, sin tomar componentes de Spring ni recursos del almacén operativo.

El experimento mantiene prevención todo-o-nada y stock suficiente: mide planificación y contención de recursos, sin introducir faltantes de mercancía, recepción o conflictos provocados. No predice directamente el comportamiento de una operación con desabastecimiento.

### Carga y reproducibilidad

La configuración predeterminada es semilla **42**, **300 pedidos**, **4 llegadas por minuto simulado**. Se permiten 1–1000 pedidos y tasas entre 0,1 y 120. Un `Random` local y el catálogo ordenado por ID generan:

- Intervalos exponenciales `−ln(1−U) × 60/tasa`, acumulados antes de cuantizar a segundos. Varias llegadas pueden coincidir en el mismo segundo.
- Niveles con mezcla aproximada 5/15/30/30/20 %. Los primeros cinco pedidos cubren los niveles 1–5 cuando la carga tiene al menos cinco pedidos.
- 1–4 productos distintos por pedido y 1–40 unidades por línea.

Los IDs locales `CMP-000001`… no consumen ni reinician el secuencial global de pedidos. Una huella SHA-256 identifica la secuencia completa de llegadas, niveles, productos y cantidades. Misma configuración y versión del generador deben producir la misma huella y los mismos resultados completos.

### Hilos reales y tiempo lógico

Acelerar únicamente `Thread.sleep` seguiría dejando las medidas sujetas al planificador del sistema operativo. Por eso el comparador usa `RelojComparativo`:

1. Incorpora todas las llegadas del instante actual.
2. Ordena candidatos mediante la política real e intenta adquirir sus recursos reales con semáforos y rollback.
3. Cada pedido admitido ejecuta un `TrabajadorPedido` en su propio hilo de plataforma, reteniendo su `Concesion` hasta terminar.
4. Los trabajadores esperan en una `Condition` por sus plazos de tiempo lógico. El conductor espera también en una condición hasta que todos estén dormidos o hayan finalizado.
5. El reloj salta a la próxima llegada o finalización. Las finalizaciones de ese instante liberan sus recursos y registran estadísticas antes de la siguiente evaluación de la cola.

Cada trabajador acumula sus ticks propios, incluso si el reloj salta varios ticks: no se pierde duración ni se introduce retraso por despertar tarde en tiempo real. El número de trabajadores activos está limitado por los recursos disponibles. Al terminar o interrumpirse la corrida se liberan concesiones, se interrumpen trabajadores restantes y se espera su cierre. Un plazo de diez segundos reales para alcanzar reposo detecta fallos; no participa en las métricas.

El conductor reproduce la evaluación conservadora del trabajo: salta candidatos no factibles. Con stock suficiente y recursos de capacidades constantes, los puntos relevantes para volver a asignar son las llegadas y liberaciones; aging se recalcula en cada evaluación. La interfaz en vivo mantiene su despachador y su reloj originales.

### Métricas y fórmulas

Para el pedido `i`, `esperaᵢ = inicioᵢ − llegadaᵢ`. La duración de trabajo sigue siendo unidades × 30 segundos simulados. Se reportan media y máximo de espera, media por nivel y SLA global y por nivel.

- Cumplimiento de SLA: `esperaᵢ ≤ objetivo(nivelᵢ)`.
- Indicador de espera excesiva denominado «inanición» en el plan: `esperaᵢ > 2 × objetivo(nivelᵢ)`. Para Exprés, cuyo objetivo es cero, toda espera positiva cuenta. Una corrida finita no demuestra inanición indefinida.
- Horizonte `T`: última finalización menos primera llegada; incluye intervalos ociosos.
- Throughput: `pedidos completados × 60 / T`.
- Ocupación de un recurso `r`: `100 × Σ duración de pedidos que retienen r / (capacidadᵣ × T)`.
- Ocupación media: suma de segundos-unidad ocupados dividida entre `26 × T`, en porcentaje; es ponderada por unidades, no el promedio simple de seis porcentajes.

La ocupación es acumulada sobre la corrida, a diferencia de la fotografía instantánea del panel de operaciones. Si un nivel no tiene muestras, la tabla lo indica y la gráfica omite su barra.

### Resultados reproducibles de referencia

Configuración 42 / 300 / 4, ejecutada sin Spring el 21/10/2026:

`57015b22436e54fbf00ca3f3274ccd8f7c6d7983fec90bbd92f751c5d7701844`

| Política | Espera media (s) | Espera máxima (s) | SLA (%) | Pedidos/min sim | Ocupación media (%) | Espera > 2× SLA | Horizonte (s) |
|---|---:|---:|---:|---:|---:|---:|---:|
| Prioridad estricta | 100354,99 | 217467 | 6,33 | 0,080340 | 39,38 | 276 | 224048 |
| Prioridad con aging | 97557,11 | 219695 | 7,67 | 0,079132 | 38,79 | 265 | 227468 |
| SJF | 69910,13 | 224699 | 17,33 | 0,078064 | 38,26 | 238 | 230580 |
| EDF | 100040,43 | 217475 | 5,67 | 0,080343 | 39,38 | 277 | 224040 |

Espera media por nivel, en segundos:

| Política | Exprés | Prioritario | Estándar | Programado | Económico |
|---|---:|---:|---:|---:|---:|
| Prioridad estricta | 3745,13 | 15001,61 | 56108,90 | 121180,24 | 186988,94 |
| Prioridad con aging | 96096,67 | 99135,21 | 102635,01 | 95927,57 | 92788,82 |
| SJF | 64718,20 | 64279,07 | 74759,32 | 72182,95 | 63388,41 |
| EDF | 22389,13 | 32705,04 | 62396,58 | 108237,05 | 185044,20 |

**Conclusión:** esta carga satura ampliamente los recursos. SJF reduce la espera media y logra el mayor SLA global; bajo ese criterio es la recomendación de esta corrida, aunque presenta la mayor espera máxima. Prioridad estricta protege mejor a Exprés y concentra las demoras en los niveles bajos. Aging reduce la espera media de Económico de 186988,94 a 92788,82 segundos, a costa de esperar más en niveles altos; ninguna política corrige por sí sola la sobrecarga ofrecida. EDF no gana en SLA en esta muestra: este sistema no expropiativo y con múltiples recursos no cumple los supuestos que permitirían afirmar optimalidad general.

Para elegir una política operativa hay que fijar primero el objetivo (SLA global, Exprés, equidad o espera media) y repetir el experimento con distintas semillas y tasas. La pantalla genera una conclusión de cada corrida, con criterio SLA y desempate por espera media; en empate total conserva el orden de políticas. No presenta esta muestra como una recomendación universal de producción.

### API y ciclo de vida

`POST /api/comparacion` admite una configuración opcional y devuelve **202** con `id` y estado `EJECUTANDO`. Campos omitidos usan valores predeterminados:

```json
{"semilla":42,"cantidadPedidos":300,"tasaLlegadasPorMinuto":4}
```

`GET /api/comparacion` devuelve `SIN_EJECUTAR`, `EJECUTANDO`, `COMPLETADA` o `ERROR`. Al completar incluye configuración, huella, alcance, cuatro resultados y conclusión. El servicio conserva sólo el último trabajo y rechaza otro concurrente con **409**; datos fuera de rango producen **400**. Al cerrar Spring interrumpe el ejecutor y sus trabajadores liberan sus recursos. No se almacena historial persistente.

El panel consulta cada 500 ms mientras hay trabajo. Al desmontarse cancela consultas y temporizadores; el trabajo del backend continúa, y volver a la pestaña recupera su estado. Los errores incluyen reintento manual.

### Evidencia de verificación

La suite incluye resultados completos idénticos al repetir la configuración por defecto, cambios de huella con otra semilla, secuencial global intacto, cálculo manual de esperas y ocupación, procesamiento concurrente con capacidad limitada, liberación de hilos y rechazo de trabajos solapados. Pruebas HTTP verifican 202/GET, validaciones y que inventario, almacén, recursos, política, reloj, eventos y contador de creados en vivo no cambien por el experimento. El transporte React verifica envío de configuración y propagación de 409.

La inspección visual de la tabla, gráfica y navegación sigue pendiente en un navegador local debido a las restricciones del entorno del agente.
