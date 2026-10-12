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
