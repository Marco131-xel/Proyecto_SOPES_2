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
| DEC-012 | Planificación EDF (Earliest Deadline First) para optimización de SLA | Maximizar el porcentaje global de cumplimiento de acuerdos de nivel de servicio (SLA). | 1. Heurísticas estáticas de prioridad.<br>2. EDF dinámico según el instante absoluto de vencimiento de SLA ($Creación + SLA_{nivel}$). | El pedido cuya fecha límite vence primero es atendido primero. | Las garantías clásicas de EDF exigen supuestos que no se cumplen aquí (expropiación y recursos independientes); el cumplimiento SLA se mide experimentalmente. |
| DEC-013 | Acumuladores `LongAdder` para métricas y telemetría de alto rendimiento | Registrar miles de transacciones y cálculos de SLA concurrentemente sin cuellos de botella. | 1. `synchronized` global para métricas.<br>2. `AtomicLong` con bucles CAS.<br>3. `LongAdder` con contadores divididos en celdas por hilo (*striped counters*). | `LongAdder` para contadores y sumadores + `AtomicLong` para valores extremos (máximos). | Minimiza la contención de memoria en arquitecturas SMP multicore; reduce la contención de actualizaciones respecto de un único contador bajo alta carga; no garantiza escalabilidad lineal. |
| DEC-014 | Paso de Mensajes Inmutables con Cola Acotada (Productor-Consumidor IPC) | Comunicar Recepción e Inventario sin compartir estructuras internas mutables y tolerar velocidades desiguales (R10). | 1. Estructura de inventario compartida con locks globales.<br>2. Cola infinita en memoria (riesgo de OutOfMemory).<br>3. `ArrayBlockingQueue<MensajeRecepcion>` acotada con contrapresión (`put()` bloqueante). | `ArrayBlockingQueue(20)` acotada transmitiendo records `MensajeRecepcion` inmutables. | Modelo Productor-Consumidor clásico con búfer acotado (Bounded Buffer) de Dijkstra. Aplica contrapresión (backpressure) natural: si Recepción es más rápida, se bloquea en `put()`, los camiones esperan en patio y no hay pérdida de datos ni bloqueo indefinido. |
| DEC-015 | Asignación Indexada No Contigua y Mapa de Bits en Almacén | Administrar el espacio físico del almacén (240 celdas) evitando la fragmentación externa (R13). | 1. Asignación contigua estricta en el mismo pasillo (riesgo severo de fragmentación externa).<br>2. Asignación encadenada con punteros en cada celda.<br>3. Asignación Indexada con tabla de bloques y `BitSet` de celdas libres. | Asignación indexada no contigua: cada lote mantiene su tabla de bloques `(Ubicacion, unidades)` sobre un mapa de bits (`BitSet`). | Analogía con tablas indexadas de bloques de archivo y marcos de páginas, sin implementar un sistema de archivos completo. Elimina la fragmentación externa al 100%, admitiendo lotes dispersos en pasillos no contiguos. |
| DEC-016 | Reserva Atómica de Stock de Dos Fases (Prevención de Sobreventa) | Evitar condiciones de carrera donde dos pedidos compitan por las mismas existencias físicas en almacén. | 1. Descontar stock directamente al completar el pedido.<br>2. Verificación sin lock previo.<br>3. Protocolo de reserva atómica de dos fases: $Disponible = Existencias - Reservas$. | Reserva atómica previa bajo `lockStock`: si no hay suficiente para todas las líneas, aborta y hace rollback; si hay, reserva y luego consume al completar. | Principio de transaccionalidad atómica y exclusión mutua de recursos no renovables. Elimina condiciones de carrera de lectura-modificación-escritura (Check-then-Act), garantizando cero sobreventa. |
| DEC-017 | Despertar Reactivo de Pedidos Condicionados por E/S (R12) | Activar pedidos en `EN_ESPERA_STOCK` tan pronto ingrese mercancía al almacén sin sondeo activo. | 1. Hilos de pedido en busy-waiting consultando stock.<br>2. Temporizador periódico de reintento de stock.<br>3. Señal reactiva: el hilo consumidor de Inventario notifica al monitor del Despachador. | Inventario llama a `despachador.despertar()` inmediatamente después de incorporar un lote físico al almacén. | Paradigma de E/S orientada a interrupciones / señales de finalización de E/S. Transición directa de estado `EN_ESPERA_STOCK` (I/O Wait) a `EN_ESPERA_RECURSOS` (Ready Queue). |
| DEC-018 | Algoritmo Matricial de Detección de Deadlock con Múltiples Instancias (R14) | Detectar formalmente ciclos de bloqueo mutuo cuando existen múltiples instancias por tipo de recurso. | 1. Detección por timeout arbitrario (falsos positivos).<br>2. Búsqueda de ciclos simples en grafos dirigidos (solo válida para instancia única).<br>3. Algoritmo de matrices $Disponible$, $Asignacion$ y $Solicitud$. | Algoritmo matricial de marcado de Silberschatz / Coffman ejecutado cada 500 ms sobre los procesos en ejecución. | Algoritmo formal de Sistemas Operativos para detección de interbloqueos con recursos multi-instancia de complejidad $O(m \times n^2)$. Deduce formalmente qué procesos no podrán terminar jamás con el inventario de recursos disponible. |
| DEC-019 | Provocación Controlada y Determinista de Deadlock (R14) | Provocar un interbloqueo reproducible al 100% para demostración en defensa oral. | 1. Generar miles de pedidos aleatorios esperando que choquen por azar.<br>2. Simulación gráfica ficticia sin bloqueo real de hilos.<br>3. Aislamiento de unidades sobrantes a modo MANTENIMIENTO + inyección de pedidos con orden cruzado de retención y espera. | Modo sin prevención + unidades en mantenimiento hasta dejar 1 Montacargas y 1 Carga + Pedido A [MTC, CRG] y Pedido B [CRG, MTC]. | Demostración práctica de las 4 Condiciones de Coffman: exclusión mutua estricta (1 unidad), retención y espera (pausa entre adquisiciones), no expropiación (hilos en `semaphore.acquire()`) y espera circular. |
| DEC-020 | Recuperación Manual por Terminación de Víctima (Rollback y Reencolado / R15) | Resolver el interbloqueo permitiendo que el usuario elija qué operación continúa. | 1. Abortar todos los procesos del sistema.<br>2. Elección automática arbitraria.<br>3. Interrupción controlada de la víctima (`Thread.interrupt`), rollback mediante `Concesion` y reencolado. | El usuario selecciona el pedido ganador; la víctima es interrumpida, libera lo retenido de inmediato en su `finally` y vuelve a la cola de espera (`vecesReencolado++`). | Estrategia de recuperación por terminación parcial de procesos con punto de restauración (Checkpointing / Rollback). La liberación de la víctima destruye la espera circular y permite al ganador completar exitosamente. |

---

## Registro Detallado de Decisiones

### DEC-001 a DEC-013 (Fases 0 a 3)
*(Ver secciones anteriores para el detalle formal de las decisiones DEC-001 a DEC-013)*.

---

### DEC-014: Paso de Mensajes Inmutables con Cola Acotada (R10)
- **Problema:** El requisito R10 exige comunicar Recepción e Inventario sin tocar estructuras internas ajenas, operando limpiamente incluso si Recepción es significativamente más rápida que Inventario.
- **Alternativas:**
  1. *Inventario compartido con cerrojo global*: Recepción modificaría directamente el mapa del almacén, rompiendo la separación de áreas y aumentando la contención de sincronización.
  2. *Cola ilimitada (`LinkedBlockingQueue` sin tope)*: Si los camiones llegan a un ritmo superior al de almacenamiento, la cola acumularía mensajes indefinidamente consumiendo memoria hasta provocar `OutOfMemoryError`.
  3. *Búfer acotado con paso de mensajes inmutables*: `ArrayBlockingQueue(20)` transfiriendo records `MensajeRecepcion` inmutables.
- **Elección:** `ArrayBlockingQueue<MensajeRecepcion>` con capacidad 20.
- **Fundamento Teórico:** Problema del Búfer Acotado (Bounded-Buffer / Productor-Consumidor) formulado por Dijkstra. La llamada `put()` suspende al productor cuando el búfer alcanza su capacidad máxima, ejerciendo contrapresión física hacia el patio de camiones. Como el consumidor siempre drena mensajes y no retiene recursos requeridos por el productor, se demuestra la ausencia de inanición y de bloqueo permanente.

---

### DEC-015: Asignación Indexada No Contigua y Mapa de Bits en Almacén (R13)
- **Problema:** Un lote de $N$ unidades puede requerir múltiples celdas físicas en el almacén ($6\times 4\times 10 = 240$ ubicaciones). Si se exigiera contigüidad física en el mismo pasillo o nivel, la liberación de lotes antiguos dejaría "huecos" insuficientes para lotes grandes aun teniendo celdas libres en total (Fragmentación Externa).
- **Alternativas:**
  1. *Asignación contigua estricta*: Sufre de fragmentación externa y requiere algoritmos de compactación costosos.
  2. *Asignación encadenada*: Cada celda guarda un puntero a la siguiente. Dificulta el acceso directo y la auditoría visual de la UI.
  3. *Asignación Indexada*: El lote almacena una tabla indexada de pares `(Ubicacion, unidades)`. La búsqueda de celdas libres se hace sobre un `BitSet` de 240 bits mediante Primer Ajuste (First-Fit).
- **Elección:** Asignación indexada no contigua con `BitSet` y `ReentrantReadWriteLock`.
- **Fundamento Teórico:** Analogía con la asignación indexada de bloques de archivo y la paginación en memoria virtual. Permite alojar un lote en celdas separadas sin requerir contigüidad, erradicando al 100% la fragmentación externa a cambio de una pequeña fragmentación interna acotada únicamente a la última celda del lote.

---

### DEC-016: Reserva Atómica de Stock de Dos Fases
- **Problema:** Si dos pedidos concurrentes requieren existencias del mismo producto y ambos comprueban el stock libre antes de descontarlo, puede producirse una condición de carrera (Check-then-Act) que provoque la sobreventa de artículos inexistentes.
- **Alternativas:**
  1. *Descontar directamente al momento de despachar*: Si el pedido falla posteriormente por falta de recursos de empaque o montacargas, el inventario quedaría desfasado.
  2. *Reservas sin lock global*: Condiciones de carrera entre lecturas y escrituras simultáneas.
  3. *Protocolo atómico de reserva en dos fases*: Un pedido consulta y aparta stock ($Reservas += Cantidad$) antes de lanzar al trabajador; al terminar el pedido, se consolida consumiendo el stock físico; si el pedido se cancela o reencola, la reserva se revierte de inmediato.
- **Elección:** Protocolo de reservas atómicas bajo `lockStock` (`ReentrantLock(true)`).
- **Fundamento Teórico:** Protocolo local de reserva y compromiso (no una transacción distribuida Two-Phase Commit) sobre recursos compartidos no renovables, garantizando la invariante matemática:
$$\text{Stock Libre} = \text{Existencias Físicas} - \text{Stock Reservado} \ge 0$$

---

### DEC-017: Despertar Reactivo de Pedidos Condicionados por E/S (R12)
- **Problema:** Cuando un pedido no puede iniciar por falta de existencias físicas, no debe consumir CPU en espera activa ni demorar en despacharse una vez llegue el camión de suministro correspondiente.
- **Alternativas:**
  1. *Sondeo periódico ciego (Polling)*: Desperdicia ciclos de procesador y genera latencia innecesaria.
  2. *Señal reactiva desde el consumidor de E/S*: En cuanto el hilo de Inventario procesa un lote y actualiza las existencias físicas, emite una señal al monitor del Despachador (`despachador.despertar()`).
- **Elección:** Notificación reactiva basada en señales de eventos.
- **Fundamento Teórico:** Mapeo con el manejo de interrupciones de E/S en el kernel: un proceso en estado Bloqueado por E/S (`EN_ESPERA_STOCK`) pasa inmediatamente a Listo (`EN_ESPERA_RECURSOS` / Ready) tras la finalización de la transferencia de datos.

---

### DEC-018: Algoritmo Matricial de Detección de Deadlock (R14)
- **Problema:** En presencia de recursos con múltiples instancias (3 montacargas, 2 áreas de carga, etc.), la detección simple de ciclos sobre un grafo de asignación puede arrojar falsos positivos (un ciclo no implica interbloqueo si otra instancia del recurso se libera).
- **Alternativas:**
  1. *Detección por watchdog / timeout*: Si un hilo tarda más de N segundos, se asume bloqueado. Inadecuado: confunde procesos de larga duración con interbloqueos genuinos.
  2. *Algoritmo de detección matricial de Silberschatz*: Utiliza el vector de recursos disponibles $V$, la matriz de asignación $A$ y la matriz de solicitud $S$. Simula el despacho optimista de procesos que pueden terminar; los que no logran terminar son formalmente declarados en interbloqueo.
- **Elección:** Algoritmo matricial implementado en `DetectorDeadlock.java` con generación del grafo RAG (`AristaAsignacion`).
- **Fundamento Teórico:** Algoritmo canónico de detección de interbloqueos para múltiples instancias. Modela el estado global del sistema operativo identificando exactamente el conjunto de procesos atrapados en espera circular irresoluble.

---

### DEC-019: Provocación Controlada y Determinista de Deadlock (R14)
- **Problema:** Los interbloqueos suelen ser no deterministas y difíciles de reproducir en una defensa ante profesores si las probabilidades de concurrencia temporal no coinciden exactamente.
- **Alternativas:**
  1. *Simulación cosmética visual*: Pintar en la UI que hay deadlock sin que los hilos estén realmente bloqueados en el sistema operativo. (Inaceptable para SO2).
  2. *Saturación de instancias y colisión directa*: Reducir temporalmente las unidades disponibles de Montacargas y Carga a exactamente 1 unidad cada una mediante el estado `MANTENIMIENTO`, e inyectar Pedido A (Pesado) con orden $[MTC, CRG]$ y Pedido B (Voluminoso) con orden $[CRG, MTC]$.
- **Elección:** Saturación a 1 y 1 + órdenes cruzados en modo sin prevención.
- **Fundamento Teórico:** Al dejar 1 sola unidad disponible de cada recurso, el problema se reduce al caso clásico de instancia única donde las 4 condiciones de Coffman se fuerzan con 100% de certeza: A retiene MTC y espera CRG; B retiene CRG y espera MTC.

---

### DEC-020: Recuperación Manual por Terminación y Retroceso de Víctima (R15)
- **Problema:** Una vez detectado el interbloqueo, el operador debe poder resolverlo de manera dirigida permitiendo que el proceso elegido continúe y el otro retroceda limpiamente.
- **Alternativas:**
  1. *Terminar el proceso completo sin reencolar*: Pérdida del pedido del cliente.
  2. *Expropiación de recursos en caliente sin interrumpir el hilo*: En Java, `Semaphore` no permite arrebatar un permiso a un hilo sin su cooperación.
  3. *Interrupción controlada (`Thread.interrupt`)*: La víctima seleccionada captura `InterruptedException`, ejecuta su cláusula `finally` liberando de inmediato su concesión (`concesion.close()`) y se reencola (`vecesReencolado++`).
- **Elección:** Interrupción de la víctima con liberación LIFO e incremento de contador de reencolados.
- **Fundamento Teórico:** Recuperación de interbloqueos por terminación selectiva de procesos y retroceso a punto de control (Rollback to Ready State). La liberación de los recursos de la víctima rompe la condición de Espera Circular, permitiendo al proceso ganador avanzar y finalizar.

---

### Fase 6 — API REST, SSE e integración React (21/10/2026)

Se retomaron los cambios sin commit que quedaron después de `8ce46b6` (Fase 5). Por instrucción del estudiante, **el agente no crea commits ni prepara el índice**. La implementación de F6 está integrada; su revisión visual en navegador sigue pendiente. El comparador funcional pertenece a F7.

### DEC-021: Contratos de telemetría y errores HTTP
- **Problema:** React esperaba un objeto con nivel/nombre/SLA, mientras Jackson serializaba `NivelServicio` como texto; además, algunos comandos trataban errores HTTP como éxitos.
- **Alternativas:** Cambiar la serialización global del dominio o exponer DTOs explícitos en la frontera HTTP.
- **Elección:** `ClienteDTO`, `EstadoSistema` y respuestas JSON con `mensaje`; validación de niveles, velocidad, tasa, cantidades y órdenes de adquisición. Respuestas 400 para datos inválidos, 404 para entidades ausentes y 409 para conflictos de estado.
- **Fundamento:** Separación entre representación e implementación. La API transmite valores y copias de cada componente, sin entregar a la UI sus estructuras internas de sincronización. Las instantáneas de componentes se toman por separado: no constituyen una transacción global del motor.

### DEC-022: SSE con respaldo por sondeo y cierre controlado
- **Problema:** Una desconexión dejaba datos antiguos en pantalla sin una alternativa de actualización; una excepción no capturada en una tarea periódica podía detener futuras publicaciones.
- **Alternativas:** Sólo sondeo, sólo SSE o SSE con sondeo durante reconexión.
- **Elección:** Evento `estado` cada 500 ms, reconexión nativa de `EventSource` y `GET /api/estado` cada 1,5 s durante la desconexión. La pantalla distingue SSE, sondeo y falta de conexión. Se cancelan temporizadores, consultas pendientes, emisores y publicador al cerrar.
- **Fundamento:** Comunicación unidireccional por mensajes; tolerancia a fallos del transporte sin cambiar el motor concurrente. Cada cliente conserva como máximo una consulta de respaldo en curso.

### DEC-023: Registro acotado de eventos reales
- **Problema:** Inferir eventos comparando fotos cada 500 ms puede omitir pedidos breves o transiciones entre publicaciones.
- **Alternativas:** Reconstruir eventos en React o registrarlos donde ocurren.
- **Elección:** `RegistroEventos` mantiene los últimos 100 eventos con secuencia e instante simulado, protegido por monitor. Despacho, recepción, inventario, detector y resolución registran eventos reales. La misma foto los entrega por SSE y sondeo.
- **Fundamento:** Exclusión mutua para actualización de estructuras compartidas y uso acotado de memoria. `Despachador.registrarPedido` centraliza el ingreso y contabiliza pedidos manuales, automáticos y del escenario una sola vez.

### DEC-024: Consistencia al operar desde la interfaz
- **Problema:** La pausa detenía el reloj visible pero las esperas ya iniciadas terminaban; líneas repetidas podían reservar por encima del stock; los lotes seguían mostrando celdas liberadas o reutilizadas.
- **Alternativas:** Impedir estos usos en el formulario o mantener las invariantes en el motor.
- **Elección:** Esperas que consultan el reloj simulado durante el trabajo; suma de cantidades por producto antes de reservar; validación de que el orden de adquisición sea una permutación exacta de los recursos; fotografías de lotes que incluyen sólo sus celdas actuales y retiro de lotes agotados. Al cerrar se interrumpen y esperan los trabajadores para liberar concesiones.
- **Fundamento:** Una interfaz no sustituye las invariantes de concurrencia. La comprobación y reserva permanecen dentro de `lockStock`; la identidad del lote evita tratar una ubicación reutilizada como pertenencia del lote anterior.

### Verificación de F6 y límite de alcance
- Suite Java: 31 pruebas sin errores, incluidas 8 de F6 contra el motor real mediante MockMvc. Incluyen SSE inicial, clientes, catálogo, pedido, recepción, validaciones, escenario/resolución HTTP, reservas repetidas, lotes reutilizados, contadores e historial y pausa del reloj.
- Frontend: 3 pruebas de transporte para errores HTTP, conmutación SSE/sondeo y cancelación al desmontar; compilación TypeScript/Vite.
- Mockito usa `mock-maker-subclass` en pruebas: no necesitamos mockear clases finales ni adjuntar un agente a la JVM; esto evita el fallo de auto-adjunción del entorno restringido.
- El entorno del agente deniega sockets locales (`Operation not permitted`) y el lanzamiento del navegador. No se afirma una revisión visual ni una demo con ambos servidores conectados. Recorrido pendiente en `docs/verificacion_fase6.md`.
- El panel de comparación identifica F7 y no muestra resultados inventados. Los manuales y la revisión general de calidad corresponden a F8.

---

### Fase 7 — Comparador de políticas (21/10/2026)

Se continuó después del commit del estudiante `b84dc9f` (Fase 6). Se implementaron motor aislado, REST asíncrono y panel React. Se mantiene la instrucción de no crear commits ni preparar el índice. La revisión visual continúa pendiente.

### DEC-025: Reloj lógico coordinado con trabajadores reales
- **Problema:** misma semilla no garantiza métricas idénticas si el tiempo depende de `Thread.sleep` y del orden de ejecución del SO.
- **Alternativas:** medir corridas reales aceleradas aceptando dispersión; reemplazar el trabajo por un bucle secuencial; coordinar los trabajadores existentes con un reloj lógico.
- **Elección:** `RelojComparativo` avanza por llegadas y finalizaciones. Cada `TrabajadorPedido` sigue siendo un hilo real y retiene concesiones sobre los semáforos reales; `Condition` sincroniza al conductor con el reposo de los trabajadores. Cada hilo acumula sus plazos propios; todas las finalizaciones del instante liberan antes del nuevo despacho.
- **Fundamento:** ejecución concurrente coordinada, exclusión mutua y comunicación mediante condiciones. Se elimina del dato experimental la variación del reloj de pared sin cambiar el trabajo proporcional ni las políticas. Se reutilizan `TrabajadorPedido`, `GestorRecursos`, `ListaEspera`, políticas y `Estadisticas`; el conductor compara candidatos factibles ante eventos de llegada/liberación.

### DEC-026: Aislamiento y carga experimental controlada
- **Problema:** comparar sobre el motor en vivo alteraría inventario, reservas, métricas e IDs y mezclaría cargas distintas.
- **Alternativas:** pausar y reiniciar el estado global o crear objetos independientes por política.
- **Elección:** `SimuladorComparativo` sin Spring genera una carga inmutable con catálogo ordenado, semilla local y huella SHA-256. Cada corrida crea pedidos con IDs `CMP-*`, reloj, cola, recursos y estadísticas nuevos. Stock suficiente y prevención activa aíslan la variable política. Se conserva sólo un resultado y se limita la carga a 1000 pedidos.
- **Fundamento:** control de variables para comparación de planificadores; el secuencial global y los componentes vivos nunca se reinician. Las métricas incluyen espera por nivel, máximo, SLA, throughput, ocupación acumulada y espera superior a 2× SLA. Se documenta el caso Exprés (objetivo 0) y que una carga finita no prueba inanición indefinida.

### DEC-027: Trabajo asíncrono y conclusión basada en resultados
- **Problema:** un POST bloqueante dificulta la UI; múltiples clientes pueden acumular experimentos, y una recomendación fija no refleja la carga elegida.
- **Alternativas:** cola ilimitada de trabajos, petición larga o admisión exclusiva con consulta de estado.
- **Elección:** POST devuelve 202, GET consulta; un ejecutor acepta un único trabajo y rechaza solapamientos con 409. React recupera el trabajo al volver a la pestaña y cancela sólo su consulta al desmontarse. La conclusión recomienda por SLA y desempata por espera media, explicando sus límites. Empates totales conservan el orden fijo de políticas.
- **Fundamento:** separación entre admisión, ejecución y observación; uso acotado de recursos y recuperación del cliente sin duplicar trabajos. Se corrigieron comentarios de SJF/EDF para no extrapolar garantías de un modelo ideal a múltiples recursos sin expropiación.

### Evidencia de F7

39 pruebas Java y 5 del frontend aprobadas; TypeScript/Vite compila. Se verificaron repetibilidad exacta con 300 pedidos, cálculos manuales, paralelismo limitado por recursos, cero hilos residuales, exclusión de trabajos, cancelación, errores HTTP y aislamiento respecto al sistema en vivo. Se ejecutó también el comparador desde un programa Java sin Spring para registrar resultados reales en `docs/manual_tecnico.md` (borrador incremental de la sección 7). La guía visual está en `docs/verificacion_fase7.md`; no se afirma validación en navegador desde el entorno restringido.

---

### Fase 8 — Calidad y manuales (22/10/2026)

Se retomó el commit del estudiante `c4dd90c` (Fase 7). El agente mantiene la instrucción de no hacer commits, git add ni push. La revisión deja manuales de usuario y técnico en Markdown y PDF, diagramas vectoriales y matriz de aceptación. No se marca realizada una inspección de navegador ni se inventan capturas; siguen en el recorrido de Fase 9.

### DEC-028: Herencia de nivel, generador y reloj monotónico
- **Problema:** el generador podía crear pedidos con un nivel distinto al del cliente, utilizaba intervalos fijos y compartía RNG/configuración sin una disciplina común. El reloj dependía de hora de calendario.
- **Alternativas:** documentar las diferencias o corregir el generador sin modificar las políticas ni el comparador.
- **Elección:** elegir nivel ponderado y después cliente del mismo nivel; 1–4 líneas y 1–40 unidades; intervalo exponencial; sincronizar RNG y admisión, tasa visible con volatile e identidad de hilo para impedir que una instancia anterior siga admitiendo tras reinicio. El reloj vivo usa nanoTime; la pausa también impide nuevas llegadas automáticas. Se expone la semilla en la barra.
- **Fundamento:** consistencia del dominio, visibilidad de memoria y exclusión de operaciones compuestas. Un origen monotónico mide duración sin saltos por ajuste de fecha. La semilla fija selección aleatoria; sólo el reloj lógico del comparador fija también los tiempos. Sus resultados de F7 se conservan.

### DEC-029: Barrera y recuperación que no reconstruye el ciclo
- **Problema:** pausas cercanas no garantizan coordinación entre hilos bajo cualquier planificación, y una víctima incremental puede volver a crear espera circular al reintentar.
- **Alternativas:** confiar en la pausa, simular el bloqueo o coordinar la primera retención.
- **Elección:** CyclicBarrier de dos participantes después del primer recurso. Se consume una sola vez por pedido. El aislamiento de mantenimiento se revierte si falla. Tras validar el ganador se activa prevención antes de interrumpir víctimas, cuyos reintentos son todo-o-nada. Restaurar recursos retira mantenimiento mediante el comando de prevención existente.
- **Fundamento:** sincronización explícita entre fases; se fuerza retención simultánea sin depender de equidad temporal del SO. Recuperación por interrupción y rollback rompe el ciclo y la prevención evita recrearlo. Las pruebas eligen cada uno de los dos ganadores y comprueban finalización y permisos recuperados.

### DEC-030: Errores semánticos, trazas y fotografías inmutables
- **Problema:** errores de protocolo podían traducirse como 500, algunas trazas sólo imprimían mensajes, listas publicadas podían modificarse y consumo parcial no tomaba el lock del almacén.
- **Alternativas:** multiplicar clases de excepción para todos los resultados o distinguir errores reales de esperas normales.
- **Elección:** PedidoInvalidoException, ClienteNoEncontradoException y ConflictoNoExisteException se usan en caminos reales; falta de stock/recurso permanece como decisión de cola. Se traducen 400/404/409 y también 405/415. El error inesperado deja traza y devuelve un mensaje general. Copias de listas y mapa protegen la foto; consumo parcial usa el write lock del almacén. Un proxy HTML fallido conserva el estado HTTP en el frontend.
- **Fundamento:** las condiciones esperadas de planificación no son fallos excepcionales. Inmutabilidad en la frontera y exclusión de lectura/escritura evitan cambios desde observadores. Se evita crear excepciones sin uso para «stock insuficiente» o «almacén lleno», que ya tienen estados de espera; es una desviación intencional de los nombres sugeridos en el plan.

### DEC-031: Documentación reproducible sin navegador
- **Problema:** se requieren manuales en PDF y diagramas, pero el entorno impide abrir puertos y lanzar navegador.
- **Alternativas:** omitir PDF, generar capturas ficticias o producir documentos desde fuentes versionadas y registrar lo no comprobado.
- **Elección:** scripts/generar_manuales.py usa Python estándar y groff/tbl/pic instalados. Markdown y SVG son fuentes legibles; .pic conserva diagramas vectoriales en PDF. Se verifican metadatos, extracción de texto y páginas renderizadas. Capturas reales y dos recorridos visuales completos quedan explícitamente pendientes.
- **Fundamento:** trazabilidad y construcción reproducible de entregables. Los diagramas son explicaciones, nunca evidencia de una pantalla que no se verificó.

### Evidencia y defensa

La revisión ejecutó 45 pruebas Java y 7 del frontend sin fallos; el build de TypeScript/Vite termina. Se añadieron regresiones de herencia de nivel, repetición de productos con semilla, fotografías inmutables, pausa de llegadas, errores 405/415 y recuperación con cualquiera de los ganadores. La relación requisito → evidencia está en docs/aceptacion.md. Las quince preguntas de defensa de la sección 12 del plan tienen respuesta en la sección 12 de docs/manual_tecnico.md; ese texto forma parte también del PDF.

Los límites se mantienen visibles: datos en memoria, cola pendiente sin admisión acotada, diagnóstico con fotografías parciales y comparador con stock suficiente. No se declara todo el proyecto listo para entrega hasta completar las capturas y verificaciones visuales de Fase 9.


### Límite del empaquetado comprobado en F8

`mvn package` compiló y ejecutó otra vez las 45 pruebas sin fallos. El paso posterior de JAR no pudo resolver `org.apache.maven.shared:file-management:3.1.0` y no puede escribir su marcador de descarga en `~/.m2` (sólo lectura). No existe un JAR nuevo generado en esta sesión. No se instalaron herramientas globales, no se pidió elevación ni se cambió el pom para eludirlo. Completar la caché y empaquetar en el entorno del estudiante queda registrado para Fase 9.

---

### Fase 9 — Preparación de entrega y ensayo

Se retomó el commit del estudiante `f68e3b5` (Fase 8). Se mantiene la prohibición de git add, commit y push por parte del agente. La preparación del paquete es verificable; la fase no se declara cerrada sin JAR y recorrido visual.

### DEC-032: Paquete trazable con alcance explícito
- **Problema:** no es correcto llamar entrega ejecutable a un ZIP de fuentes si falta el JAR, ni incluir archivos privados al comprimir todo el directorio.
- **Alternativas:** comprimir indiscriminadamente el árbol, detener toda preparación o crear una selección permitida de fuentes/artefactos con estado verificable.
- **Elección:** scripts/preparar_entrega.py selecciona código, configuración pública, lockfile, pruebas, documentación y build. Excluye Git, node_modules, cachés y credenciales. Reconoce un JAR Spring Boot por manifiesto, lanzador, BOOT-INF y dependencias; --exigir-jar rechaza su ausencia. Genera SHA-256 individual y del ZIP, comprueba CRC y usa metadatos fijos para repetir el mismo archivo con las mismas entradas.
- **Fundamento:** trazabilidad e integridad de entregables; el hash detecta corrupción o diferencias, sin presentarse como firma de autoría. Los informes JUnit se resumen sin exportar sus propiedades de proceso. El empaquetador verifica artefactos, no reemplaza pruebas ni certifica pantallas.

### Límites de ejecución y guion

Se volvió a intentar Vite: listen EPERM en 127.0.0.1:5173. Se aplicó la habilidad local Playwright; npx offline no tiene el metadato npm necesario y la CLI ya presente en caché tampoco pudo crear el daemon por EROFS en ~/.cache/ms-playwright. No hubo navegador ni capturas. Maven offline puede compilar, pero maven-jar-plugin conserva dependencias ausentes. No se cambiaron las versiones del pom, no se instaló globalmente ni se usó un JAR simple para aparentar un ejecutable.

El guion docs/guion_demo.md reserva dos registros de ensayo pendientes, alterna ganador en deadlock y especifica comprobación de comparación, reconexión y proyector. docs/verificacion_fase9.md explica la preparación en una máquina con caché escribible y dependencias disponibles. El paquete local de fuentes no implica cierre visual ni entrega binaria completa.

### Evidencia del paquete preparado

Se reconfirmaron 45 pruebas Java con Maven offline y 7 de frontend, además del build de Vite. El paquete de 137 entradas se extrajo en una carpeta temporal y su manifiesto completo se verificó con sha256sum. Dos preparaciones con las mismas entradas generaron bytes idénticos. --exigir-jar rechazó la ausencia de ejecutable como corresponde. El hash actual y estado de contenido se generan junto al ZIP en output/entrega; no se confunde esta validación de integridad con cierre visual de Fase 9.
