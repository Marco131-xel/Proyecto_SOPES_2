# Bitácora de Decisiones Técnicas y Arquitectura — REDXela

> **Proyecto Final — Sistemas Operativos 2**  
> Este documento registra todas las decisiones de diseño, patrones de sincronización, compromisos de concurrencia y su fundamentación teórica para la defensa del proyecto.

---

## Índice de Decisiones

| ID | Decisión | Problema | Alternativas evaluadas | Elección | Fundamento Teórico |
|---|---|---|---|---|---|
| DEC-001 | Stack tecnológico y desacoplamiento de concurrencia | Implementar simulación concurrente con interfaz web reactiva. | 1. Node.js (event loop monohilo).<br>2. C/C++ con pthreads.<br>3. Java con Spring Boot + `java.util.concurrent`. | Java 21 + Spring Boot (solo REST/SSE) + `java.util.concurrent` nativo. | Hilos reales a nivel de kernel/SO (OS threads en Java platform threads), soporte nativo de monitores, semáforos contadores y colas bloqueantes sin delegar la sincronización a un framework externo. |
| DEC-002 | Modelo de comunicación tiempo real Backend-Frontend | Enviar el estado cambiante de recursos y pedidos a la UI web con baja sobrecarga. | 1. Polling HTTP constante.<br>2. WebSockets bidireccionales.<br>3. Server-Sent Events (SSE). | Server-Sent Events (`SseEmitter`) con snapshot periódico (~500 ms) + polling fallback. | El flujo principal de telemetría es unidireccional (servidor → cliente). SSE opera sobre HTTP estándar, maneja reconexión nativa y minimiza la complejidad frente a WebSockets manteniendo reactividad en tiempo real. |
| DEC-003 | Reloj de Simulación Acelerado (`SimClock`) continuo | Permitir observar vencimientos de SLA (hasta 120 min) sin esperas reales de horas ni saltos temporales. | 1. Simulación por eventos discretos (DES sin hilos concurrentes).<br>2. Dormir menos tiempo arbitrariamente sin noción de reloj global.<br>3. Reloj proporcional diferencial continuo. | Reloj diferencial continuo: $T_{sim} = T_{base\_sim} + \Delta t_{real} \times factor$. | Preserva la noción de tiempo continuo y sincronización preemptiva del SO. Al recalcular dinámicamente los anclajes base, garantiza monotonicidad estricta y continuidad temporal sin discontinuidades. |
| DEC-004 | Agregación de recursos en pedidos multilínea | Determinar qué recursos y cuántas unidades requiere un pedido con productos heterogéneos. | 1. Sumar unidades de recursos por cada producto (riesgo de agotar recursos y deadlock intra-pedido).<br>2. Exigir un solo tipo de mercancía por pedido.<br>3. Unión de tipos de recursos (1 unidad por tipo). | Unión de conjuntos de recursos de las líneas del pedido: $\bigcup Recursos(L_i)$, solicitando 1 unidad de cada tipo. | Modela adecuadamente la estación de trabajo integrada del centro de distribución: cada pedido activo es atendido por un equipo multidisciplinario compuesto por máximo 1 encargado, 1 montacargas, etc., eliminando contención circular interna. |
| DEC-005 | Modelado de estados del pedido análogo a estados de proceso | Representar el ciclo de vida de un pedido desde su recepción hasta su despacho. | 1. Estados simplificados (Pendiente, En Proceso, Fin).<br>2. Máquina de estados completa del ciclo de vida de procesos del SO. | 6 estados: `EN_ESPERA_STOCK` (I/O Wait), `EN_ESPERA_RECURSOS` (Ready), `EN_PROCESO` (Running), `COMPLETADO` (Terminated), `BLOQUEADO` (Deadlock), `CANCELADO`. | Mapeo 1:1 con la teoría de procesos del sistema operativo: desacopla la espera por datos/memoria (stock) de la espera por CPU/dispositivos (recursos de empaque/montacargas). |
| DEC-006 | Semáforos Contadores Justos (`Semaphore(capacidad, true)`) para pools de recursos | Gestionar pools de recursos con múltiples instancias homogéneas (10 encargados, 6 empaques, 3 montacargas, etc.). | 1. Arreglo de locks individuales con busy-waiting.<br>2. Monitor con variable contador simple sin justicia.<br>3. `Semaphore` contador con política FIFO justa (`fair=true`). | `Semaphore(capacidad, true)` para el control de permisos + `ReentrantLock` secundario para el mapeo visual de unidades físicas. | El semáforo contador de Dijkstra es la primitiva canónica de SO para pools de recursos homogéneos. La equidad FIFO (`fair=true`) previene la inanición (starvation) de hilos en la cola de adquisición del semáforo. |
| DEC-007 | Prevención de Deadlock mediante adquisición Todo-o-Nada atómica | Prevenir bloqueos mutuos garantizando que el sistema no sufra interbloqueos en modo seguro. | 1. Ordenamiento lineal global estricto de recursos (jerarquía de Dijkstra).<br>2. Detección continua y expulsión automática preventiva.<br>3. Solicitud atómica Todo-o-Nada con Rollback inmediato. | Adquisición atómica Todo-o-Nada (`tryAcquire` de todos los recursos requeridos bajo un lock de arbitraje; rollback si alguno falla). | Niega la condición de Coffman de **Retención y Espera (Hold and Wait)**: un proceso jamás retiene recursos mientras espera indefinidamente por otros. El interbloqueo es matemáticamente imposible. |
| DEC-008 | Concesión AutoCloseable (Patrón RAII) | Evitar fugas de recursos (resource leaks) si un hilo de pedido falla o es interrumpido abruptamente. | 1. Liberación manual dependiente de cada punto de retorno en el código.<br>2. Hilo recolector de basura de recursos.<br>3. Objeto `Concesion implements AutoCloseable` en bloque `finally`. | `Concesion` liberando en orden inverso (LIFO) de forma atómica e idempotente. | Principio RAII (Resource Acquisition Is Initialization). Garantiza que ante `InterruptedException` (resolución de deadlock) o errores de runtime, el 100% de los permisos se reintegren a los semáforos sin orfandad. |
| DEC-009 | Despachador reactivo basado en Monitor y planificador Work-Conserving | Despachar pedidos en espera sin sobrecarga de consumo de CPU por sondeo constante (busy waiting). | 1. Bucle infinito con `Thread.sleep` fijo (polling activo ineficiente).<br>2. Hilos que compiten directamente sin despachador central.<br>3. Despachador centralizado con monitor `ReentrantLock` + `Condition` y algoritmo conservador del trabajo. | Monitor con `Condition.await(timeout)` que despierta ante eventos puntuales, saltando pedidos no factibles para atender los que sí tienen recursos libres. | Patrón Monitor de Hoare/Brinch Hansen. El enfoque *work-conserving* garantiza que ningún recurso quede ocioso si existe al menos un proceso en la cola que pueda utilizarlo, maximizando el throughput global. |
| DEC-010 | Algoritmo de Envejecimiento (Aging) para prevención de Inanición | Evitar que los pedidos de Nivel 4 y 5 queden postergados indefinidamente por la llegada constante de pedidos Nivel 1 y 2. | 1. Cuotas fijas por nivel (round-robin por nivel).<br>2. Envejecimiento dinámico: $NivelEfectivo = \max(1, Nivel - \lfloor espera/T_{aging} \rfloor)$. | Prioridad dinámica decreciente numéricamente con el tiempo de espera acumulado. | Concepto clásico de Aging de Sistemas Operativos. Garantiza que la prioridad de todo proceso bloqueado en cola aumente monótonamente con el tiempo, asegurando la propiedad de equidad (*fairness*). |
| DEC-011 | Planificación SJF (Shortest Job First / SPT) por unidades de pedido | Minimizar el tiempo medio de espera de la cola global de pedidos. | 1. Estimar ráfaga por histórico.<br>2. Menor cantidad de unidades totales primero ($duración = unidades \times 30s$). | Menor cantidad de unidades de productos primero, con desempate por nivel de servicio y llegada FIFO. | El teorema de planificación SPT (Shortest Processing Time / SJF) demuestra que ordenar por menor tiempo de servicio minimiza el tiempo promedio de espera global ($W_{prom}$). |
| DEC-012 | Planificación EDF (Earliest Deadline First) para optimización de SLA | Maximizar el porcentaje global de cumplimiento de acuerdos de nivel de servicio (SLA). | 1. Heurísticas estáticas de prioridad.<br>2. EDF dinámico según el instante absoluto de vencimiento de SLA ($Creación + SLA_{nivel}$). | El pedido cuya fecha límite vence primero es atendido primero. | Teorema de optimalidad de Liu y Layland (1973): EDF es óptimo para programación de tareas con fechas límite dinámicas, minimizando la fracción de pedidos que violan su SLA. |
| DEC-013 | Acumuladores `LongAdder` para métricas y telemetría de alto rendimiento | Registrar miles de transacciones y cálculos de SLA concurrentemente sin cuellos de botella. | 1. `synchronized` global para métricas.<br>2. `AtomicLong` con bucles CAS.<br>3. `LongAdder` con contadores divididos en celdas por hilo (*striped counters*). | `LongAdder` para contadores y sumadores + `AtomicLong` para valores extremos (máximos). | Minimiza la contención de memoria en arquitecturas SMP multicore; elimina el falso intercambio de líneas de caché (*false sharing*) y la degradación por reintentos CAS bajo alta contención. |

---

## Registro Detallado de Decisiones

### DEC-001: Stack tecnológico y concurrencia pura en Java
- **Problema:** El proyecto requiere demostrar conceptos profundos de concurrencia de Sistemas Operativos (exclusión mutua, semáforos contadores, productor-consumidor acotado, detección y prevención de deadlock, algoritmos de scheduling).
- **Alternativas:**
  1. *Node.js*: Concurrencia basada en bucle de eventos monohilo; no utiliza hilos del SO para computación paralela de pedidos ni primitivas de sincronización atómicas reales.
  2. *C / C++ (Pthreads)*: Excelente para SO, pero la integración con una interfaz gráfica web moderna y reactiva en el tiempo asignado incrementa el riesgo de desarrollo.
  3. *Java 21 con `java.util.concurrent`*: Hilos de plataforma reales mapeados 1:1 a hilos del sistema operativo (POSIX threads en Linux), soporte exhaustivo de `Semaphore`, `ReentrantLock`, `Condition`, `ArrayBlockingQueue`. Spring Boot se limita a servir HTTP/SSE y DI; el motor concurrente es autónomo.
- **Elección:** Java 21 con Spring Boot 3 para backend, React + TypeScript + Vite para frontend.
- **Fundamento Teórico:** Los hilos de plataforma proporcionan concurrencia real preemptiva gobernada por el planificador del kernel, permitiendo simular contención de recursos, bloqueos mutuos reales y cambios de contexto genuinos.

---

### DEC-002: Server-Sent Events (SSE) para visualización en tiempo real
- **Problema:** La interfaz debe reflejar estados de recursos, pedidos en cola, progreso de procesamiento y alertas de deadlock sin recargar la página.
- **Alternativas:**
  1. *Polling HTTP*: Fácil de implementar, pero genera sobrecarga innecesaria de encabezados HTTP repetidos.
  2. *WebSockets*: Protocolo bidireccional completo, pero requiere manejo de desconexión/reconexión manual y mayor complejidad en el backend para un flujo predominantemente de solo lectura (telemetría).
  3. *Server-Sent Events (SSE)*: Canal unidireccional persistente sobre HTTP con reconexión nativa del navegador.
- **Elección:** SSE enviando un `EstadoSistema` inmutable cada 500 ms con fallback a `GET /api/estado`.
- **Fundamento Teórico:** Modelo cliente-servidor con transmisión de flujo de datos push; desacopla el ciclo de ejecución del despachador del ciclo de renderizado de la UI.

---

### DEC-003: Reloj de Simulación Acelerado (`SimClock`)
- **Problema:** Los pedidos manejan SLAs simulados de 0 a 120 minutos. Una prueba en tiempo real tardaría horas, pero una simulación puramente matemática sin hilos reales perdería el valor pedagógico del SO.
- **Alternativas:**
  1. *Simulación discreta orientada a eventos (DES)*: No utiliza hilos reales en espera; calcula saltos temporales matemáticos. No demuestra concurrencia real de hilos del SO.
  2. *Tiempos fijos arbitrarios pequeños*: Rompe la correspondencia con las metas de SLA del enunciado (10, 30, 60, 120 min).
  3. *Reloj proporcional diferencial continuo*: Define un factor de aceleración $F$ (por defecto $60\times$: 1 s real = 1 min sim). Cuando un hilo duerme, duerme $\Delta t_{sim} / F$ ms reales. Al cambiar $F$ o pausar, se preserva $T_{base\_sim}$.
- **Elección:** Reloj proporcional diferencial continuo en `SimClock` protegido por `ReentrantLock`.
- **Fundamento Teórico:** Sincronización temporal lineal. Garantiza monotonicidad $T_{sim}(t_2) \ge T_{sim}(t_1)$ para $t_2 \ge t_1$, evitando viajes al pasado al variar la velocidad de simulación.

---

### DEC-004: Agregación de recursos en pedidos multilínea
- **Problema:** Si un pedido contiene un producto Frágil (ENCARGADO, CONTROL_CALIDAD, EMPAQUE) y uno Pesado (ENCARGADO, MONTACARGAS, CARGA), ¿cuántos encargados requiere?
- **Alternativas:**
  1. *Suma acumulativa de unidades*: El pedido exigiría 2 encargados simultáneos. Si hay muchos pedidos multilínea, los 10 encargados se agotarían casi al instante y aumentaría la posibilidad de inanición no deseada.
  2. *Restringir a 1 solo tipo de mercancía por pedido*: Limita el realismo de los carritos de compra de los clientes.
  3. *Unión de conjuntos con 1 unidad por tipo de recurso*: El pedido requiere la unión $\{ENCARGADO, CONTROL\_CALIDAD, EMPAQUE, MONTACARGAS, CARGA\}$, solicitando exactamente 1 unidad de cada tipo.
- **Elección:** Unión de tipos de recursos (1 unidad de cada recurso participante).
- **Fundamento Teórico:** Representación de necesidades de recursos como un vector booleano de demanda unitaria en la matriz de necesidades del algoritmo de detección/asignación de recursos, modelando un flujo coordinado por estación.

---

### DEC-005: Modelado de estados del pedido análogo a estados de proceso
- **Problema:** El enunciado requiere pedidos en espera por falta de stock y por falta de recursos, pedidos en proceso, pedidos bloqueados en deadlock y pedidos completados.
- **Alternativas:**
  1. *Cola única con banderas*: Difícil de distinguir visualmente y de planificar eficientemente.
  2. *Máquina de estados de procesos de SO*:
     - `EN_ESPERA_STOCK`: Bloqueado esperando I/O (llegada de existencias físicas).
     - `EN_ESPERA_RECURSOS`: Listo (Ready) en la cola de planificación para ser despachado.
     - `EN_PROCESO`: Ejecución (Running) reteniendo recursos de trabajo.
     - `BLOQUEADO`: Interbloqueo circular (Coffman) en espera de recursos retenidos por otro.
     - `COMPLETADO`: Proceso terminado exitosamente.
     - `CANCELADO`: Proceso abortado.
- **Elección:** Máquina de 6 estados implementada en `EstadoPedido` con variables volátiles y transiciones atómicas.
- **Fundamento Teórico:** Mapeo formal con el diagrama de transición de estados de un proceso (Silberschatz / Tanenbaum).

---

### DEC-006: Semáforos Contadores Justos (`Semaphore(capacidad, true)`) para pools de recursos
- **Problema:** Administrar la asignación concurrente de recursos multi-instancia (10 encargados, 6 empaques, etc.) garantizando que no se asignen más unidades que las disponibles y que los procesos no sufran inanición en la cola de espera del recurso.
- **Alternativas:**
  1. *Locks individuales por unidad con sondeo*: Requiere que cada hilo recorra un arreglo de locks y use `tryLock()`, generando sobrecarga de CPU y comportamiento no equitativo.
  2. *Monitor centralizado sin justicia*: Un monitor simple con `notifyAll()` despierta a todos los hilos, y el hilo más rápido gana la contención, provocando *thundering herd problem* e inanición de hilos lentos.
  3. *`Semaphore(capacidad, true)` de Java*: Semáforo contador de Dijkstra con cola FIFO justa interna implementada sobre el `AbstractQueuedSynchronizer` (AQS).
- **Elección:** `Semaphore(capacidad, true)` para la contención numérica y un `ReentrantLock` ligero para asociar la unidad física específica (ej. "Montacargas-1").
- **Fundamento Teórico:** Los semáforos justos implementan la disciplina estricta FIFO en la cola de bloqueados, garantizando la propiedad de libertad de inanición (*starvation-freedom*).

---

### DEC-007: Prevención de Deadlock mediante adquisición Todo-o-Nada atómica
- **Problema:** Evitar que ocurran interbloqueos durante la operación regular del centro de distribución cuando múltiples pedidos compiten por recursos compartidos.
- **Alternativas:**
  1. *Orden global de recursos (Jerarquía de Dijkstra)*: Obliga a adquirir los recursos en un orden numérico ascendente estricto (ej. primero ENCARGADO, luego MONTACARGAS, luego CARGA). Elimina la espera circular, pero permite retener recursos ociosos mientras se espera el siguiente.
  2. *Adquisición Todo-o-Nada atómica con Rollback*: Un proceso debe solicitar la totalidad de sus recursos en un único intento atómico. Si algún recurso no está libre, se liberan inmediatamente todos los recursos adquiridos en el intento y el proceso vuelve a esperar.
- **Elección:** Adquisición atómica Todo-o-Nada implementada en `GestorRecursos.intentarAdquirirTodoONada`.
- **Fundamento Teórico:** De acuerdo con las 4 condiciones necesarias de Coffman (Exclusión Mutua, Retención y Espera, No Expropiación, Espera Circular), el interbloqueo se vuelve matemáticamente imposible si se invalida al menos una condición. La adquisición todo-o-nada niega la condición de **Retención y Espera (Hold and Wait)**, asegurando que ningún proceso retenga recursos si no puede proceder inmediatamente.

---

### DEC-008: Concesión AutoCloseable (Patrón RAII)
- **Problema:** Garantizar que cuando un hilo trabajador finaliza, falla o es interrumpido (por ejemplo, al ser elegido víctima en la resolución de un deadlock), libere el 100% de los recursos retenidos sin provocar fugas que bloqueen el sistema a largo plazo.
- **Alternativas:**
  1. *Bloques de liberación manual*: Propenso a errores humanos si existen múltiples cláusulas `return` o excepciones intermedias.
  2. *Clase `Concesion implements AutoCloseable`*: Envuelve las asignaciones obtenidas y ejecuta la restitución en su método `close()` dentro de bloques `try-with-resources` o `try ... finally`.
- **Elección:** `Concesion implements AutoCloseable` con liberación LIFO e idempotencia atómica con `AtomicBoolean`.
- **Fundamento Teórico:** Principio RAII (Resource Acquisition Is Initialization). Vincula el ciclo de vida de los recursos del SO al ámbito léxico de ejecución del proceso, asegurando la invariante de conservación de permisos.

---

### DEC-009: Despachador reactivo basado en Monitor y planificador Work-Conserving
- **Problema:** Coordinar la asignación de pedidos en espera a los recursos disponibles minimizando la latencia de despacho y sin desperdiciar ciclos de procesador.
- **Alternativas:**
  1. *Bucle de espera activa (Busy-waiting)*: Quema el 100% de un núcleo de CPU consultando continuamente la lista de espera.
  2. *Temporizador periódico ciego (Polling)*: Despacha cada N milisegundos fijos, introduciendo latencia artificial si los recursos se liberan antes.
  3. *Monitor de sincronización con `ReentrantLock` y `Condition`*: El despachador duerme en `Condition.await()` y es despertado instantáneamente ante eventos reales (llegada de pedido, liberación de recurso por un trabajador, cambio de política).
- **Elección:** Monitor reactivo con timeout auxiliar de 250 ms (para re-evaluación de aging).
- **Fundamento Teórico:** Modelo de Monitores de Hoare/Brinch Hansen. El algoritmo es *work-conserving*: si el primer pedido de la cola no puede ejecutarse por falta de un recurso específico, evalúa a los siguientes candidatos factibles, impidiendo que recursos libres queden desaprovechados.

---

### DEC-010: Algoritmo de Envejecimiento (Aging) para prevención de Inanición
- **Problema:** En prioridad estricta, si entran continuamente pedidos de Nivel 1 o 2, los pedidos de Nivel 5 sufren inanición (starvation) indefinida.
- **Alternativas:**
  1. *Round-robin por nivel*: No respeta los acuerdos comerciales de prioridad para clientes que pagan más.
  2. *Envejecimiento proporcional discreto*: Cada intervalo de tiempo $T_{aging}$ que un pedido espera sin ser atendido, su prioridad efectiva se incrementa en 1 unidad numérica hacia Nivel 1.
- **Elección:** $NivelEfectivo = \max(1, NivelOriginal - \lfloor esperaSegundos / T_{aging} \rfloor)$.
- **Fundamento Teórico:** Principio de envejecimiento (Silberschatz). Al aumentar monótonamente la prioridad efectiva con el tiempo de espera, se garantiza que cualquier proceso eventualmente alcance la prioridad máxima y sea despachado, erradicando la inanición.

---

### DEC-011: Planificación SJF (Shortest Job First / SPT)
- **Problema:** Maximizar la cantidad de pedidos despachados por unidad de tiempo y minimizar el tiempo medio de permanencia en la cola.
- **Alternativas:**
  1. *SJF con estimación histórica*: Complejo e innecesario dado que conocemos exactamente la cantidad de artículos.
  2. *SJF determinista por total de unidades*: Dado que el trabajo es estrictamente proporcional a las unidades ($30\text{ s sim/unidad}$), el pedido con menor número total de unidades tiene la ráfaga más corta.
- **Elección:** Ordenar por `totalUnidades` ascendente; desempate por nivel de servicio y FIFO.
- **Fundamento Teórico:** Teorema de Smith / Conway: La regla SPT (Shortest Processing Time) minimiza matemáticamente la suma ponderada y no ponderada de tiempos de espera en sistemas monoprocesador/estación.

---

### DEC-012: Planificación por Fecha Límite Más Cercana (EDF)
- **Problema:** Reducir la tasa de incumplimiento de acuerdos SLA en pedidos con metas temporales fijas.
- **Alternativas:**
  1. *Prioridad fija por SLA*: No toma en cuenta cuándo llegó el pedido.
  2. *EDF Dinámico*: $Deadline = InstanteCreacion + SLA_{nivel}$. El pedido cuya fecha límite vence antes va de primero.
- **Elección:** `PoliticaEDF` calculando la fecha límite absoluta contra el reloj simulado.
- **Fundamento Teórico:** Teorema de Liu y Layland (1973). EDF es el algoritmo óptimo en sistemas de tiempo real para cumplir restricciones de plazo (deadlines).

---

### DEC-013: Acumuladores `LongAdder` para métricas de alto rendimiento
- **Problema:** Múltiples hilos concurrentes terminando pedidos y actualizando métricas globales simultáneamente sin generar contención ni degradar el throughput.
- **Alternativas:**
  1. *Locks de exclusión mutua*: Serializan todas las escrituras estadísticas.
  2. *`AtomicLong`*: Provoca colisiones continuas en el bucle CAS bajo alta concurrencia.
  3. *`LongAdder`*: Distribuye las operaciones de suma en celdas internas asociadas a los hilos activos.
- **Elección:** `LongAdder` para contadores globales y por nivel; `AtomicLong` solo para el cálculo de máximos (`accumulateAndGet`).
- **Fundamento Teórico:** Mitigación de contención de memoria (*cache-line bouncing* y *false sharing*) en arquitecturas multinúcleo con memoria compartida (SMP).
