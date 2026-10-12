# Bitácora de Decisiones Técnicas y Arquitectura — REDXela

> **Proyecto Final — Sistemas Operativos 2**  
> Este documento registra todas las decisiones de diseño, patrones de sincronización, compromisos de concurrencia y su fundamentación teórica para la defensa del proyecto.

---

## Índice de Decisiones

| ID | Decisión | Problema | Alternativas evaluadas | Elección | Fundamento Teórico |
|---|---|---|---|---|---|
| DEC-001 | Stack tecnológico y desacoplamiento de concurrencia | Implementar simulación concurrente con interfaz web reactiva. | 1. Node.js (event loop monohilo).<br>2. C/C++ con pthreads.<br>3. Java con Spring Boot + `java.util.concurrent`. | Java 21 + Spring Boot (solo REST/SSE) + `java.util.concurrent` nativo. | Hilos reales a nivel de kernel/SO (OS threads en Java platform threads), soporte nativo de monitores, semáforos contadores y colas bloqueantes sin delegar la sincronización a un framework externo. |
| DEC-002 | Modelo de comunicación tiempo real Backend-Frontend | Enviar el estado cambiante de recursos y pedidos a la UI web con baja sobrecarga. | 1. Polling HTTP constante.<br>2. WebSockets bidireccionales.<br>3. Server-Sent Events (SSE). | Server-Sent Events (`SseEmitter`) con snapshot periódico (~500 ms) + polling fallback. | El flujo principal de telemetría es unidireccional (servidor → cliente). SSE opera sobre HTTP estándar, maneja reconexión nativa y minimiza la complejidad frente a WebSockets manteniendo reactividad en tiempo real. |

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
