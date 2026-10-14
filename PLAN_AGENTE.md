# PLAN DE TRABAJO PARA EL AGENTE — Proyecto Final Sistemas Operativos 2 (REDXela)

> **Lee este archivo completo antes de escribir código.** Es el contrato del proyecto: contiene los requisitos, las decisiones de diseño ya tomadas (no las vuelvas a preguntar), el orden de trabajo y los criterios de aceptación.
> Carpeta de trabajo: `proyecto_final/` (vacía al iniciar). Fecha de entrega: **martes 27 de octubre de 2026**. Hoy es domingo 11 de octubre.

---

## 0. Reglas de trabajo del agente

1. Trabaja **por fases** (sección 9). Al terminar cada fase: compilar, correr pruebas, hacer `git commit` con mensaje claro y actualizar `docs/bitacora_decisiones.md`.
2. **Comentarios del código en español**, en cada clase y método no trivial (es requisito de la calificación). Explica el *por qué* de la sincronización, no solo el qué.
3. **Manejo de excepciones** obligatorio (sección 8.5). Ningún hilo debe morir en silencio.
4. Usa **primitivas reales de `java.util.concurrent`** (`Semaphore`, `ReentrantLock`, `Condition`, `BlockingQueue`, `ReadWriteLock`). No simules la concurrencia con bucles secuenciales.
5. **No sobre-ingenierices.** Si hay dos formas de hacer algo, elige la más simple que sea defendible teóricamente. El estudiante debe poder explicar cada línea en la defensa.
6. Mantén `docs/bitacora_decisiones.md`: por cada decisión importante anota *problema → alternativas → elección → fundamento teórico del curso*. De ahí sale el Manual Técnico.
7. Verifica el entorno antes de empezar: `java -version`, `mvn -v`, `node -v`, `git --version`. Si falta algo, dilo al estudiante y propón la alternativa; no instales cosas globales sin avisar.
8. Si algo del enunciado es ambiguo y **no** está resuelto en este plan, elige una opción razonable, anótala en la bitácora y continúa.

---

## 1. Análisis del enunciado

### 1.1 Qué es el proyecto realmente
Un **simulador concurrente** de un centro de distribución. Los pedidos son "procesos"; las estaciones, montacargas, personal, etc. son "recursos compartidos"; el almacén es "memoria/disco". El proyecto es una excusa para demostrar, con código real y una interfaz que lo haga visible, los temas del curso:

| Tema del curso | Dónde aparece en REDXela |
|---|---|
| Hilos / procesos concurrentes | Cada pedido activo se ejecuta en su propio hilo; también hilos de Recepción, Inventario, Despachador, Generador, Detector de deadlock |
| Planificación (scheduling) | Lista de espera + despachador con políticas intercambiables (prioridad estricta, aging, SJF, EDF) |
| Exclusión mutua | Escáner (1 unidad), estructuras compartidas protegidas con locks |
| Semáforos | Cada pool de recursos (6 empaque, 3 montacargas, etc.) es un semáforo contador |
| Productor–consumidor | Recepción → cola acotada → Inventario |
| Deadlock (condiciones de Coffman, detección, recuperación, prevención) | Escenario provocado, detección por algoritmo, resolución manual, prevención activable |
| Comunicación entre procesos | Paso de mensajes inmutables por cola bloqueante (sin compartir estructuras internas) |
| Administración de almacenamiento | Almacén con ubicaciones; lotes en ubicaciones **no contiguas** (asignación indexada, bitmap, fragmentación) |
| Inanición / aging | Envejecimiento de pedidos de nivel bajo |

### 1.2 Requisitos extraídos (checklist de aceptación)

**Recursos fijos:** 6 estaciones de empaque · 2 áreas de carga · 10 encargados de bodega · 3 montacargas · 4 estaciones de control de calidad · 1 escáner (compartido Recepción/Despacho) · almacén con capacidad definida por el estudiante.

- [ ] **R1** Clientes: crear cuenta y elegir nivel de servicio (1–5).
- [ ] **R2** Pedido con: ID único, cliente, nivel, productos + tipo de mercancía de cada uno, cantidad por producto y total, estado actual (+ campos extra libres).
- [ ] **R3** Generador automático de pedidos, de todos los niveles, **sin límite** de cantidad.
- [ ] **R4** Ingreso manual de un pedido específico en cualquier momento.
- [ ] **R5** Varias políticas de atención intercambiables y **comparables** (prioridad estricta, con aging, menor cantidad de artículos primero).
- [ ] **R6** Trabajo del pedido proporcional a su cantidad de unidades.
- [ ] **R7** ≥ 5 tipos de mercancía, cada uno con nombre, descripción y **combinación distinta** de recursos.
- [ ] **R8** Existe un escenario realista donde dos pedidos necesitan los **mismos dos recursos compartidos** en momentos cercanos y el sistema lo resuelve bien.
- [ ] **R9** Pedido que no obtiene recursos → **lista de espera**.
- [ ] **R10** Recepción e Inventario comunicados por **mensajes**, sin tocar estructuras ajenas; funciona si Recepción es más rápida que Inventario (sin pérdida de datos ni bloqueo indefinido).
- [ ] **R11** Catálogo de productos predefinido; desde la UI solo se elige producto + cantidad recibida.
- [ ] **R12** Tras recibir mercadería, todo es automático: Inventario actualiza existencias, asigna ubicaciones y **re-evalúa pedidos en espera** de ese producto.
- [ ] **R13** Lotes en varias ubicaciones, **no necesariamente contiguas** (pasillos/niveles).
- [ ] **R14** Deadlock **provocable a propósito**; la UI muestra recursos involucrados, operaciones bloqueadas entre sí y por qué.
- [ ] **R15** Resolución manual: el usuario elige cuál operación continúa; la otra vuelve a la lista de espera.
- [ ] **R16** UI en tiempo real (web): estado de cada recurso (disponible/en uso/por quién), pedidos en proceso con progreso y tiempo restante, lista de espera con su criterio de orden, estadísticas (completados, promedios de espera y procesamiento, ocupación), registro manual de pedidos y mercadería, visualizar y resolver conflicto.
- [ ] **R17** Backend en **Java con hilos y sincronización real**; código comentado; excepciones manejadas.
- [ ] **R18** Entregables: código fuente, **Manual de Usuario**, **Manual Técnico** (infraestructura + decisiones detalladas con fundamento teórico).

### 1.3 Puntos sutiles que el enunciado esconde (no los pases por alto)
- **Tiempos objetivo** (Exprés ≈ 0, 10, 30, 60, 120 min) son *simulados*. Hace falta un **reloj de simulación acelerado** o nadie verá nunca un pedido esperando 120 minutos.
- "Sin límite de cantidad" → el sistema no puede acumular memoria infinita: conserva contadores/estadísticas agregadas y solo una ventana de los últimos N pedidos completados.
- Nivel de servicio ≠ tipo de mercancía: el nivel decide *cuándo*; el tipo decide *qué recursos*.
- El deadlock debe ocurrir **de verdad** (hilos realmente bloqueados), no ser una animación. Eso exige poder **desactivar la prevención** y adquirir recursos de uno en uno reteniendo los ya obtenidos.
- Para que el deadlock sea limpio con recursos de varias unidades, hay que saturarlos (ver 6.3).
- El escáner lo usan Recepción y Despacho: es el ejemplo natural de exclusión mutua entre "áreas".
- La calificación incluye **defender las decisiones** (sección 12 tiene preguntas probables).

---

## 2. Stack y estructura (decidido)

- **Backend:** Java 21 (si solo hay 17, usar 17), **Spring Boot 3.x** (solo para REST + SSE + inyección; **toda la concurrencia es código propio con `java.util.concurrent`**), Maven.
- **Frontend:** React + TypeScript + Vite, **Recharts** para gráficas, CSS simple (Tailwind opcional).
- **Tiempo real:** **SSE** (`SseEmitter`) que envía una *foto* completa del estado (`EstadoSistema`) cada 500 ms; el front la pinta. Fallback: polling a `GET /api/estado`.
- **Persistencia:** ninguna obligatoria. Todo en memoria (clientes, pedidos, inventario). Opcional al final: guardar clientes en un JSON. No usar base de datos para el núcleo.
- **Pruebas:** JUnit 5.

```
proyecto_final/
├─ PLAN_AGENTE.md
├─ README.md                      # cómo compilar y ejecutar
├─ docs/
│  ├─ bitacora_decisiones.md
│  ├─ manual_usuario.md
│  ├─ manual_tecnico.md
│  └─ diagramas/                  # mermaid o imágenes
├─ backend/
│  ├─ pom.xml
│  └─ src/main/java/gt/usac/redxela/
│     ├─ RedxelaApplication.java
│     ├─ config/        # ConfiguracionSimulacion (todas las constantes), SimClock
│     ├─ modelo/        # Cliente, Pedido, LineaPedido, Producto, TipoMercancia, NivelServicio, EstadoPedido
│     ├─ recursos/      # TipoRecurso, PoolRecurso, GestorRecursos, Concesion (AutoCloseable)
│     ├─ planificacion/ # PoliticaAtencion (interfaz) + 4 implementaciones, ListaEspera, Despachador
│     ├─ proceso/       # TrabajadorPedido (hilo del pedido)
│     ├─ recepcion/     # Camion, Recepcion, MensajeRecepcion
│     ├─ inventario/    # Inventario (consumidor), Lote, Reserva
│     ├─ almacen/       # Almacen, Ubicacion, Asignacion, mapa de bits, fragmentación
│     ├─ deadlock/      # DetectorDeadlock, InformeConflicto, ResolucionConflicto
│     ├─ generador/     # GeneradorPedidos (semilla configurable)
│     ├─ estadisticas/  # Estadisticas (acumuladores thread-safe), Muestras
│     ├─ comparacion/   # SimuladorComparativo (modo sin UI, reloj ultra-rápido)
│     ├─ excepciones/   # excepciones propias + ManejadorGlobal
│     └─ api/           # controladores REST, SSE, DTOs (records)
│  └─ src/test/java/... # pruebas de concurrencia y lógica
└─ frontend/
   └─ src/ (componentes por panel)
```

---

## 3. Modelo de dominio (decidido)

### 3.1 Niveles de servicio
| Nivel | Nombre | Espera objetivo (min simulados) |
|---|---|---|
| 1 | Exprés | 0 (casi instantáneo) |
| 2 | Prioritario | 10 |
| 3 | Estándar | 30 |
| 4 | Programado | 60 |
| 5 | Económico | 120 |

### 3.2 Tipos de recursos
`ENCARGADO(10)`, `EMPAQUE(6)`, `CARGA(2)`, `MONTACARGAS(3)`, `CONTROL_CALIDAD(4)`, `ESCANER(1)`. Cada **unidad** tiene nombre (`Empaque-1`…`Empaque-6`) para que la UI muestre *quién* la tiene.

### 3.3 Tipos de mercancía (6, cada uno con combinación distinta; 1 unidad de cada recurso listado)
| Tipo | Descripción | Recursos requeridos |
|---|---|---|
| FRÁGIL | Vidrio, cerámica, decoración | ENCARGADO + CONTROL_CALIDAD + EMPAQUE |
| PESADO | Electrodomésticos, herramientas | ENCARGADO + MONTACARGAS + CARGA |
| ELECTRÓNICO | Alto valor, requiere trazabilidad | CONTROL_CALIDAD + ESCANER + CARGA |
| PERECEDERO | Cadena de frío, urgente | ENCARGADO + MONTACARGAS + CONTROL_CALIDAD |
| VOLUMINOSO | Muebles, colchones | MONTACARGAS + EMPAQUE + CARGA |
| GENERAL | Ropa, accesorios, libros | ENCARGADO + EMPAQUE + CARGA |

**Contención deliberada (R8):**
- FRÁGIL y PERECEDERO comparten **ENCARGADO + CONTROL_CALIDAD**.
- PESADO y VOLUMINOSO comparten **MONTACARGAS + CARGA** ← par usado para el deadlock.

**Pedido con varios productos de distintos tipos:** el pedido requiere la **unión** de los recursos de sus líneas (1 unidad de cada tipo de recurso, sin repetir). Documentar en la bitácora.

### 3.4 Catálogo de productos (≥ 18, ~3 por tipo de mercancía)
Cada producto: `id`, `nombre`, `tipoMercancia`, `unidadesPorUbicacion` (cuántas unidades caben en una ubicación: ej. frágil 8, pesado 2, voluminoso 1, general 20). Definido en código o JSON de recursos, no editable desde la UI.

### 3.5 Pedido
Campos: `id` (secuencia atómica, formato `PED-000123`), `cliente`, `nivel`, `lineas[]` (producto, cantidad), `totalUnidades`, `estado`, `instanteCreacion`, `instanteInicio`, `instanteFin` (todos en tiempo **simulado**), `recursosRequeridos` (derivado), `recursosRetenidos`, `ordenAdquisicion` (para el modo sin prevención), `estimadoTotal`, `progreso`, `veces reencolado`, `venceSLA`.

**Estados:** `EN_ESPERA_STOCK` → `EN_ESPERA_RECURSOS` → `EN_PROCESO` → `COMPLETADO`; además `BLOQUEADO` (en deadlock) y `CANCELADO`.

### 3.6 Cliente
`id`, `nombre`, `correo`, `nivelContratado`. Los pedidos heredan el nivel de la cuenta. Crear cliente desde la UI; el generador usa un conjunto de clientes semilla de todos los niveles (y puede crear clientes nuevos).

### 3.7 Trabajo y tiempo
- **Reloj de simulación `SimClock`:** `ahoraSimulado()` = tiempo real × factor. Factor por defecto: **1 s real = 1 min simulado** (configurable 1×–600× desde la UI). Todas las esperas, objetivos y estadísticas se miden en tiempo simulado. Los hilos duermen `duracionSimulada / factor`.
- **Trabajo del pedido:** `duración = totalUnidades × SEGUNDOS_SIM_POR_UNIDAD` (ej. 30 s sim/unidad) → un pedido de 10 unidades dura 5 min sim = 5 s reales a velocidad por defecto. Ajustar para que la demo sea fluida.
- El progreso se actualiza por *ticks* (cada ~100 ms reales) para la barra y el "tiempo restante".

---

## 4. Concurrencia: diseño central (decidido)

### 4.1 Hilos del sistema
| Hilo | Función |
|---|---|
| `Despachador` (1) | Evalúa la lista de espera, aplica la política, reserva stock y recursos, lanza trabajadores |
| `TrabajadorPedido` (1 por pedido activo) | Ejecuta el pedido: retiene recursos, "trabaja", libera, reporta |
| `Recepcion` (1 hilo procesador de camiones) | Usa el escáner, empaqueta mensajes y los envía a Inventario |
| `Inventario` (1 hilo consumidor) | Consume mensajes, actualiza existencias, asigna ubicaciones, notifica al despachador |
| `GeneradorPedidos` (1) | Crea pedidos y clientes según tasa configurable |
| `DetectorDeadlock` (1, cada ~500 ms) | Analiza el estado de recursos y detecta bloqueo mutuo |
| `PublicadorEstado` (1) | Construye `EstadoSistema` y lo envía por SSE |

Usa hilos de plataforma con nombres descriptivos (`pedido-PED-000123`) para que las trazas sean legibles. `Thread.setDefaultUncaughtExceptionHandler` registra cualquier fallo.

### 4.2 `PoolRecurso`: semáforo contador con dueños
- Un `Semaphore(capacidad, true)` (justo/FIFO) por tipo de recurso.
- Una estructura `unidades[]` (nombre, dueño, desde cuándo) protegida por un `ReentrantLock` pequeño del pool, para que la UI vea quién tiene qué.
- Métodos: `tryAcquire(pedido)`, `acquire(pedido)` (bloqueante e interrumpible), `release(pedido)`, `snapshot()`.
- El **escáner** es el mismo pool con capacidad 1 (exclusión mutua).
- Opción "mantenimiento": un dueño especial `MANTENIMIENTO` que ocupa unidades (útil para el escenario de deadlock, ver 6.3).

### 4.3 Dos modos de adquisición (interruptor `prevencionDeadlock`)
**Modo PREVENCIÓN (por defecto, ON):** *todo-o-nada atómico*. Un `ReentrantLock gestorLock` serializa el intento: se hace `tryAcquire` de todos los recursos requeridos; si alguno falla, se hace **rollback** de los ya tomados y el pedido sigue en espera. Elimina *hold-and-wait* (una de las 4 condiciones de Coffman) → **deadlock imposible**.

**Modo SIN PREVENCIÓN (OFF, para la demo):** el trabajador adquiere los recursos **uno a uno** en su `ordenAdquisicion`, **reteniendo** los ya obtenidos mientras espera el siguiente (`acquireInterruptibly`), con una pausa corta (ej. 1,5 s reales) entre adquisiciones para que el choque sea reproducible. Aquí las 4 condiciones de Coffman se cumplen y el deadlock puede ocurrir de verdad.

Fundamento para la defensa: la prevención se logra *negando hold-and-wait*; alternativa documentada: orden global de recursos (negaría espera circular). Anotar ambas en la bitácora y explicar por qué se eligió una.

### 4.4 Ciclo de vida de un trabajador (siempre con `try/finally`)
```
try {
   adquirir recursos (según modo)           // puede bloquearse
   estado = EN_PROCESO; instanteInicio = ahora
   repeat: dormir tick, actualizar progreso // trabajo ∝ unidades
   consumir reserva de stock y liberar ubicaciones vaciadas
   estado = COMPLETADO; registrar estadísticas
} catch (InterruptedException e) {           // elegido como víctima de deadlock
   liberar lo retenido; devolver a lista de espera (reencolado++)
} finally {
   liberar recursos retenidos (Concesion.close()) ; notificar al Despachador
}
```
Usa una clase `Concesion implements AutoCloseable` que registra qué unidades retiene el pedido, para no fugar recursos jamás.

---

## 5. Planificación (decidido)

### 5.1 Interfaz (patrón Strategy)
```java
interface PoliticaAtencion {
    String nombre();
    String descripcion();                       // se muestra en la UI como "criterio de orden"
    Comparator<Pedido> comparador(long ahoraSim);
}
```
Cambiable **en caliente** desde la UI; la lista de espera se reordena de inmediato.

### 5.2 Políticas a implementar
1. **PRIORIDAD_ESTRICTA** — menor nivel primero; desempate FIFO (por llegada). Riesgo: inanición del nivel 5.
2. **PRIORIDAD_CON_AGING** — nivel efectivo = `max(1, nivel − floor(espera / T_aging))` con `T_aging` configurable (ej. proporcional al objetivo del nivel). Evita inanición; explicar la fórmula.
3. **MENOR_CANTIDAD_PRIMERO (SJF)** — menor `totalUnidades` primero (estimación del trabajo, como SJF); desempate por nivel. Incluye variante con aging opcional (documentar la inanición de pedidos grandes).
4. **EDF – Fecha límite más cercana** — `deadline = llegada + esperaObjetivo(nivel)`; el que vence primero va primero. Vincula directamente con la tabla de niveles. (Si falta tiempo, esta es la única prescindible; las otras 3 son obligatorias.)

### 5.3 Despachador (algoritmo)
```
loop (se despierta por evento: pedido nuevo / recurso liberado / stock llegó / cambio de política / tick de 250 ms para aging):
  candidatos = listaEspera ordenada con política.comparador(ahoraSim)
  para cada pedido en candidatos:
      si tryReservarStock(pedido) falla  → estado EN_ESPERA_STOCK; continuar
      si tryAdquirirTodo(pedido) falla   → deshacer reserva de stock; estado EN_ESPERA_RECURSOS; continuar
      si ambos ok → sacar de la lista, lanzar TrabajadorPedido
```
- Es **conservador del trabajo** (*work-conserving*, saltea pedidos no factibles para no desperdiciar recursos); anotar en la bitácora el riesgo de inanición por recursos escasos y cómo el aging lo mitiga.
- Sincronización: `ReentrantLock` + `Condition` (monitor) para dormir hasta que ocurra un evento; nunca *busy-waiting*.
- **En modo SIN PREVENCIÓN** el despachador lanza el trabajador sin pre-adquirir; el trabajador adquiere uno a uno (4.3).

### 5.4 Nivel 1 (Exprés)
"Casi instantáneo": prioridad máxima en toda política (excepto que cada política pueda documentar lo contrario) y los pedidos exprés se evalúan en cuanto llegan (evento inmediato al despachador). No se hace expropiación de pedidos ya en proceso (documentarlo).

---

## 6. Recepción, Inventario, Almacén y Deadlock

### 6.1 Recepción → Inventario (productor–consumidor)
- La UI (o un generador de camiones opcional) crea un `Camion(producto, cantidad)` y lo deja en una cola `camionesEnPatio` (visible en la UI).
- El hilo `Recepcion` toma un camión, **adquiere el escáner** (exclusión mutua con Despacho), registra la llegada, libera el escáner y envía un `MensajeRecepcion` (record **inmutable**: productoId, cantidad, instante) a una **`ArrayBlockingQueue` acotada** (capacidad ej. 20) con `put()`.
- El hilo `Inventario` hace `take()`, procesa **a su propio ritmo** (con retardo simulado por mensaje) y no comparte estructuras con Recepción: solo se comunican por la cola.
- **Por qué cumple R10:** la cola acotada aplica *contrapresión* (si está llena, Recepción se bloquea en `put()` y los camiones esperan en el patio; no se pierde nada); como Inventario siempre drena y no depende de nada que Recepción retenga, **no hay bloqueo indefinido**. Mostrar en la UI: tamaño de la cola, camiones en patio, mensajes procesados.
- Las peticiones HTTP de la UI **nunca** se bloquean: solo agregan el camión al patio y responden 202.

### 6.2 Inventario y almacén (administración de almacenamiento)
- **Almacén:** cuadrícula de **ubicaciones** `P{pasillo}-N{nivel}-{posición}`. Por defecto **6 pasillos × 4 niveles × 10 posiciones = 240 ubicaciones** (configurable; "capacidad dada por el estudiante").
- Cada ubicación guarda **un solo producto** y hasta `unidadesPorUbicacion` unidades.
- **Asignación no contigua (R13):** un lote de N unidades necesita `ceil(N / unidadesPorUbicacion)` ubicaciones; se toman con **primer ajuste** sobre un **mapa de bits** de ubicaciones libres, **sin exigir contigüidad**. El lote guarda su **tabla de asignación** (lista `(ubicación, unidades)`) = *asignación indexada*, análoga a bloques de un archivo en disco. Documentar la analogía y la ventaja (evita fragmentación externa) y el costo (fragmentación interna en la última ubicación).
- **Métricas visibles:** % ocupación, ubicaciones libres, fragmentación (ej. número de "huecos" separados / índice de dispersión de cada lote).
- **Almacén lleno:** el mensaje no se pierde ni bloquea al consumidor; queda en `lotesPendientesDeEspacio` y se reintenta cuando una ubicación se libera. La UI lo muestra.
- **Existencias y reservas:** Inventario mantiene `existencias[producto]` y `reservado[producto]`. El despachador **reserva** stock antes de iniciar un pedido y el trabajador lo **consume** al terminar (evita vender dos veces lo mismo). Si la reserva se pierde por un reencolado, se **libera**.
- **Consumo FIFO de lotes** al completar pedidos; las ubicaciones que quedan en 0 vuelven al mapa de bits.
- Proteger con `ReentrantReadWriteLock` (muchas lecturas de la UI, pocas escrituras) o un lock único bien razonado; documentar la elección.
- **Al registrar llegada (R12):** Inventario actualiza existencias → asigna ubicaciones → **notifica al despachador** (señal) para re-evaluar los pedidos en `EN_ESPERA_STOCK`.

### 6.3 Deadlock
- **Detección** (hilo cada ~500 ms, solo relevante en modo SIN PREVENCIÓN): algoritmo clásico de **detección con múltiples instancias** (vector *Disponible*, matriz *Asignación*, matriz *Solicitud*; marcar procesos que pueden terminar; los no marcados están en deadlock). Devuelve un `InformeConflicto`: pedidos involucrados, recursos disputados, quién retiene qué y qué espera cada uno ("A retiene MONTACARGAS-1 y espera CARGA-x que retiene B…"). La UI lo dibuja como **grafo de asignación de recursos** simple (SVG) más una explicación en texto.
- **Provocarlo a propósito:**
  - Interruptor "Prevención de deadlock" en la UI.
  - Botón **"Escenario de deadlock"** (endpoint dedicado): desactiva la prevención, pone en `MANTENIMIENTO` las unidades sobrantes de MONTACARGAS y CARGA hasta dejar **1 y 1** (requiere que estén libres; si no, el escenario avisa "pausa el generador y espera a que se vacíen" o el agente implementa la alternativa de saturar con N pedidos), y crea **dos pedidos**: A = PESADO con orden `[MONTACARGAS, CARGA]` y B = VOLUMINOSO con orden `[CARGA, MONTACARGAS]`. Resultado: A retiene el montacargas y espera la carga; B retiene la carga y espera el montacargas → deadlock real entre exactamente dos operaciones.
  - También se puede provocar manualmente: formulario avanzado de pedido con "orden de adquisición" editable.
- **Resolución manual (R15):** panel "Conflicto detectado" con las operaciones en conflicto y botón **"Que continúe ésta"** por cada una. Al elegir ganador:
  1. Se **interrumpe** (`Thread.interrupt`) a la(s) otra(s); su `catch(InterruptedException)` libera lo retenido (expropiación = *recuperación por terminación de víctima/retroceso*).
  2. El perdedor vuelve a `EN_ESPERA_RECURSOS` (reencolado++).
  3. El ganador obtiene el recurso liberado y termina.
  4. Si se desea, al terminar se restablece la prevención y se liberan las unidades en mantenimiento.
- Registrar en el log y la bitácora: condiciones de Coffman cumplidas, por qué se detectó, política de elección de víctima (manual).

---

## 7. Generador, estadísticas y comparador

### 7.1 Generador automático (R3)
- Hilo con intervalo exponencial (llegadas tipo Poisson) y **tasa configurable** (pedidos/min simulado) desde la UI; iniciar/pausar.
- Mezcla de niveles configurable (por defecto ~5/15/30/30/20 %), clientes aleatorios, 1–4 líneas por pedido, cantidades 1–40 por línea, tipos variados.
- **Semilla de `Random` configurable** → permite repetir la misma carga (clave para el comparador).
- Sin tope de pedidos: guardar solo los últimos ~200 completados en memoria; el resto va a acumuladores.

### 7.2 Estadísticas (R16)
Acumuladores thread-safe (`LongAdder`/lock): pedidos creados/completados/en cola/en proceso; espera promedio y máxima **por nivel**; tiempo de procesamiento promedio; **% cumplimiento de SLA** por nivel (espera ≤ objetivo); throughput (pedidos/min sim); **ocupación por recurso** (tiempo ocupado ÷ capacidad × tiempo, instantánea y acumulada); longitud de cola en el tiempo (serie para gráfica); stock total y ocupación del almacén.

### 7.3 Comparador de políticas (R5, muy valorado)
- `SimuladorComparativo`: ejecuta **la misma carga** (misma semilla, mismos N pedidos, ej. 300) con cada política en **modo sin UI y reloj ultra-rápido**, sobre instancias aisladas del motor (no tocar el estado en vivo).
- Salida: tabla comparativa por política: espera promedio por nivel, espera máxima, % SLA cumplido, throughput, ocupación media, nº de pedidos con inanición (espera > 2× objetivo). Gráfica de barras agrupadas.
- Botón "Comparar políticas" en la UI + endpoint que devuelve resultados. Incluir en el Manual Técnico una conclusión razonada (qué política conviene y por qué).
- Requisito de diseño: el motor debe poder instanciarse **sin Spring ni UI** (inyección por constructor, sin estáticos globales mutables).

---

## 8. API, interfaz y calidad

### 8.1 API REST (sugerida)
| Método | Ruta | Función |
|---|---|---|
| GET | `/api/estado/stream` | SSE con `EstadoSistema` cada 500 ms |
| GET | `/api/estado` | Foto puntual (fallback) |
| POST/GET | `/api/clientes` | Crear / listar clientes |
| GET | `/api/catalogo` | Productos y tipos de mercancía |
| POST | `/api/pedidos` | Pedido manual |
| POST | `/api/recepcion` | Registrar llegada (producto, cantidad) |
| POST | `/api/generador/iniciar` · `/detener` · PUT `/config` | Control del generador |
| PUT | `/api/politica` | Cambiar política |
| PUT | `/api/simulacion/velocidad` | Factor del reloj |
| PUT | `/api/prevencion` | Activar/desactivar prevención |
| POST | `/api/escenarios/deadlock` | Provocar deadlock |
| GET | `/api/conflicto` | Informe actual |
| POST | `/api/conflicto/resolver` | `{ganadorId}` |
| POST/GET | `/api/comparacion` | Ejecutar / obtener resultados |

### 8.2 Interfaz (React) — paneles
1. **Barra de control:** política activa, velocidad, generador (iniciar/pausar/tasa), interruptor de prevención, botón de escenario de deadlock, reloj simulado.
2. **Recursos:** una tarjeta por tipo con sus unidades (verde libre / naranja en uso / gris mantenimiento) y **quién** la tiene (ID de pedido); barra de ocupación.
3. **En proceso:** tabla con pedido, cliente, nivel, tipo(s), barra de progreso, tiempo restante estimado.
4. **Lista de espera:** ordenada **según la política activa**, con una leyenda que explique el criterio, columnas: posición, ID, nivel (color), unidades, espera vs objetivo (semáforo verde/amarillo/rojo), motivo de espera (stock / recursos) y recurso faltante.
5. **Estadísticas:** KPIs + gráficas (Recharts): espera por nivel, ocupación por recurso, cola en el tiempo, SLA.
6. **Almacén / Inventario:** cuadrícula tipo mapa de calor (cada ubicación coloreada por producto), tabla de existencias/reservas, lotes y sus ubicaciones (para evidenciar no contigüidad), fragmentación.
7. **Recepción:** formulario (producto del catálogo + cantidad), camiones en patio, tamaño de la cola de mensajes (contrapresión visible), lotes pendientes de espacio.
8. **Pedidos y clientes:** crear cliente (nombre, correo, nivel); formulario de pedido manual (cliente, líneas producto+cantidad; opción avanzada de orden de adquisición).
9. **Conflicto / Deadlock:** alerta roja destacada, grafo de asignación, texto "quién retiene / quién espera", botones para elegir ganador.
10. **Comparador de políticas:** botón + tabla + gráfica.
11. **Log de eventos:** últimos ~100 eventos (llegadas, inicios, finales, reencolados, deadlock).

Diseño: limpio, legible en proyector (el estudiante lo presentará). Colores por nivel consistentes. Manejo de error de conexión SSE con reconexión visible.

### 8.3 Estado publicado
Un único DTO inmutable `EstadoSistema` (records) construido por `PublicadorEstado` tomando *fotos* consistentes de cada componente (cada componente expone `snapshot()` bajo su propio lock; no exponer objetos mutables).

### 8.4 Configuración centralizada
`ConfiguracionSimulacion` con todas las constantes (capacidades, factor de reloj, segundos por unidad, tamaño de cola, dimensiones del almacén, `T_aging`, pausa entre adquisiciones). Nada de números mágicos dispersos.

### 8.5 Excepciones (R17)
Excepciones propias: `PedidoInvalidoException`, `ClienteNoEncontradoException`, `StockInsuficienteException`, `AlmacenLlenoException`, `RecursoNoDisponibleException`, `ConflictoNoExisteException`. `@RestControllerAdvice` convierte a respuestas JSON claras (400/404/409). Dentro de hilos: `try/catch` por iteración del bucle, log, y `finally` que garantiza liberar recursos; `InterruptedException` se maneja restaurando el flag cuando corresponde.

### 8.6 Pruebas (JUnit 5) — mínimas obligatorias
- Un pool **nunca** entrega más unidades que su capacidad (N hilos concurrentes, miles de iteraciones).
- Adquisición todo-o-nada hace rollback correcto y no deja recursos huérfanos.
- Cada política ordena como se espera (casos pequeños deterministas) y aging evita inanición.
- Dos pedidos con orden inverso en modo sin prevención → el detector reporta deadlock; tras resolver, ambos terminan.
- Productor más rápido que consumidor: **no se pierden mensajes** y no hay bloqueo permanente.
- Asignación de almacén: lote repartido en ubicaciones no contiguas; liberar y reasignar mantiene la consistencia del mapa de bits; **no hay sobreventa** de stock bajo concurrencia.
- Al llegar stock, los pedidos en `EN_ESPERA_STOCK` se re-evalúan y arrancan.

---

## 9. Fases y calendario

| Fase | Fechas | Entregable | Criterio de "terminado" |
|---|---|---|---|
| **F0 Preparación** | Lun 12 | Estructura de carpetas, `git init`, Spring Boot que arranca, Vite que arranca, `README`, bitácora vacía | `mvn spring-boot:run` y `npm run dev` funcionan |
| **F1 Dominio y reloj** | 12–13 | Modelo, catálogo, tipos de mercancía, `SimClock`, `ConfiguracionSimulacion`, clientes | Pruebas de modelo; SimClock acelera correctamente |
| **F2 Núcleo concurrente** | 13–15 | `PoolRecurso`, `GestorRecursos` (prevención), `TrabajadorPedido`, `Despachador`, prioridad estricta, generador básico | Se ven pedidos fluir con recursos limitados, sin fugas; pruebas del 8.6 (pools, rollback) |
| **F3 Políticas y estadísticas** | 15–16 | 4 políticas, cambio en caliente, `Estadisticas` | Pruebas de orden; estadísticas coherentes |
| **F4 Recepción, inventario, almacén** | 16–18 | Productor–consumidor, escáner compartido, reservas, asignación no contigua, pendientes de espacio | Pruebas de no-pérdida y no-sobreventa; llegada de stock despierta pedidos |
| **F5 Deadlock** | 18–19 | Modo sin prevención, detector, informe, escenario, resolución | Escenario reproducible en el 100 % de los intentos; resolución manual funciona |
| **F6 API + Frontend** | 15–21 (en paralelo desde que existe `EstadoSistema`) | Todos los paneles del 8.2 | Todos los ítems R16 verificados en pantalla |
| **F7 Comparador** | 21–22 | `SimuladorComparativo` + panel | Resultados reproducibles con la misma semilla |
| **F8 Calidad y documentación** | 22–24 | Pruebas completas, revisión de comentarios y excepciones, **Manual de Usuario** y **Manual Técnico** | Checklist R1–R18 todo marcado |
| **F9 Buffer y defensa** | 25–26 | Empaquetado (zip + PDFs), ensayo de demo y preguntas de defensa | Demo de punta a punta sin fallar, 2 veces seguidas |
| **ENTREGA** | **Mar 27** | Código fuente + manuales | — |

Si el calendario se aprieta, el orden de sacrificio es: EDF → panel de log → gráficas extra. **Nunca** sacrificar: deadlock, productor–consumidor, almacén no contiguo, comparador básico, manuales.

---

## 10. Guion de demostración (verificar en F9)
1. Iniciar con generador a baja tasa; mostrar recursos y pedidos fluyendo.
2. Subir la tasa hasta saturar; mostrar lista de espera y su criterio; cambiar de política en caliente y ver el reorden.
3. Crear un pedido Exprés manual: pasa al frente.
4. Recepción: registrar llegada grande; mostrar cola de mensajes, lote repartido en ubicaciones no contiguas y pedidos en espera de stock que arrancan solos.
5. Recepción más rápida que Inventario: mostrar contrapresión sin pérdida.
6. Apagar prevención + "Escenario de deadlock": mostrar bloqueo, grafo y explicación; elegir ganador; ver al otro volver a la espera y todo terminar.
7. Ejecutar comparador y comentar conclusiones.

---

## 11. Documentación a entregar

**Manual de Usuario** (`docs/manual_usuario.md` → PDF): requisitos de ejecución, cómo iniciar backend y frontend, recorrido por cada panel con capturas, cómo registrar pedidos/clientes/mercadería, cómo cambiar política, cómo provocar y resolver el deadlock, glosario.

**Manual Técnico** (`docs/manual_tecnico.md` → PDF), con:
1. Arquitectura general e infraestructura (diagramas: componentes, hilos, flujo de un pedido, flujo Recepción→Inventario).
2. Tecnologías y por qué.
3. Modelo de dominio y tipos de mercancía (tabla de recursos).
4. **Decisiones de diseño** (de la bitácora), cada una con problema, alternativas, elección y **fundamento teórico**: hilos vs procesos, semáforos vs monitores, atómico todo-o-nada vs orden global, aging, SJF/EDF, cola acotada, asignación indexada vs contigua, detección vs prevención, reloj simulado.
5. Mapa concepto del curso → clase/método del código.
6. Algoritmo de detección de deadlock con ejemplo numérico.
7. Resultados del comparador y conclusión.
8. Pruebas realizadas, limitaciones conocidas y mejoras futuras.

---

## 12. Preguntas probables de defensa (el agente debe dejar las respuestas en la bitácora)
1. ¿Por qué un hilo por pedido y no un pool fijo? ¿Qué cambiaría?
2. ¿Qué condiciones de Coffman se cumplen en el escenario y cuál niega tu prevención?
3. ¿Por qué `Semaphore` para los recursos y `ReentrantLock` para el escáner/estructuras? ¿Son equivalentes con permiso 1?
4. ¿Por qué `ArrayBlockingQueue` y no `LinkedBlockingQueue`? ¿Qué pasa si se llena?
5. ¿Cómo garantizas que no se pierden mensajes ni se bloquea indefinidamente?
6. ¿Qué es inanición, cómo aparece en tu sistema y cómo la resuelve el aging?
7. ¿Por qué SJF es óptimo para el tiempo de espera promedio y cuál es su riesgo?
8. ¿Qué es el aging y cómo lo calculaste?
9. ¿Cómo funciona tu algoritmo de detección de deadlock? Hazlo con números.
10. ¿Qué pasa con los recursos del pedido que pierde en la resolución? ¿Cómo se libera sin fugas?
11. ¿Por qué un lote puede estar en ubicaciones no contiguas? ¿Qué estructura de datos lo permite y qué tiene que ver con sistemas de archivos?
12. ¿Cómo evitas vender dos veces la misma existencia (condición de carrera)?
13. ¿Qué es una condición de carrera y dónde existiría si quitaras tal lock?
14. ¿Por qué un reloj simulado? ¿Qué problemas trae?
15. ¿Qué política recomiendas para producción y con qué datos lo respaldas?

---

## 13. Riesgos y mitigaciones
| Riesgo | Mitigación |
|---|---|
| Deadlock no reproducible por tiempos | Pausa fija entre adquisiciones + recursos en mantenimiento hasta 1/1 |
| Fuga de recursos si un hilo falla | `Concesion` AutoCloseable + `finally` + prueba de conteo final de permisos |
| UI saturada con miles de pedidos | Mostrar solo top-N y contadores; el resto agregado |
| Comparador no reproducible | Semilla fija, sin estáticos mutables, reloj inyectado |
| Se acaba el tiempo | Orden de sacrificio de la sección 9; mantener `main` siempre ejecutable |
| El estudiante no entiende el código | Comentarios en español + bitácora de decisiones + preguntas de la sección 12 |

---

## 14. Definición de "listo"
Todo lo de R1–R18 marcado, pruebas en verde, demo del guion (sección 10) ejecutada dos veces sin fallos, `README` con instrucciones, ambos manuales en PDF, código comentado y limpio, y `git log` con historia por fases.
