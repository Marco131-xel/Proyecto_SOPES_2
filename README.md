# REDXela — Simulador concurrente de centro de distribución

Proyecto Final Sistemas Operativos 2 · USAC · Octubre 2026.

Pedidos en hilos Java reales, semáforos y monitores, cuatro políticas, recepción por mensajes, almacén indexado, deadlock dirigido y comparación reproducible. Spring Boot expone REST/SSE y React muestra once paneles distribuidos en cuatro pestañas.

## Requisitos

Java 21, Maven 3.9+, Node.js 22, npm y navegador moderno. Se verificaron Java 21.0.13, Maven 3.9.16 y Node 22.23.2. No se necesita base de datos. El uso previsto es local; los datos se pierden al reiniciar el backend.

## Ejecución

Desde `Proyecto_SOPES_2`, abrir dos terminales.

Backend:

```bash
cd backend
mvn test
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm ci
npm run dev
```

Abrir `http://localhost:5173`. El backend usa 8080 y el proxy está en `frontend/vite.config.ts`. Esperar unos segundos a que termine la recepción de stock inicial. El generador inicia apagado y la prevención activada.

## Pruebas y construcción

En `backend`, `mvn package` ejecuta pruebas y genera `target/redxela-1.0.0.jar`; se inicia con `java -jar target/redxela-1.0.0.jar`.

En este entorno, `mvn package` pasó compilación y las 45 pruebas, pero no pudo generar el JAR: falta una dependencia del plugin de empaquetado y `~/.m2` es de sólo lectura. Completar ese paso con las dependencias disponibles y una caché escribible forma parte del empaquetado pendiente de Fase 9.

En `frontend`:

```bash
npm test
npm run build
```

El build estático queda en `frontend/dist`. Fuera del servidor de desarrollo, quien lo sirva debe dirigir `/api` al backend; el proxy de Vite sólo se configura para desarrollo.

## Documentación

- [Manual de Usuario](docs/manual_usuario.md) · [PDF](docs/manual_usuario.pdf): arranque, paneles, pedidos, recepción, deadlock y comparación.
- [Manual Técnico](docs/manual_tecnico.md) · [PDF](docs/manual_tecnico.pdf): arquitectura, decisiones, algoritmos, resultados, pruebas y respuestas de defensa.
- [Matriz de aceptación R1–R18](docs/aceptacion.md) y [bitácora de decisiones](docs/bitacora_decisiones.md).
- [Verificación de interfaz](docs/verificacion_fase6.md), [comparador](docs/verificacion_fase7.md) y [capturas pendientes](docs/capturas/README.md).
- [Diagramas vectoriales](docs/diagramas/README.md).

Para regenerar PDF, instalar previamente Python 3 y groff con soporte PDF, tbl y pic. Desde la raíz del repositorio:

```bash
python scripts/generar_manuales.py
```

El script usa herramientas locales, sin navegador, dependencias pip ni red. Los PDF versionados ya están disponibles para lectura sin esas herramientas.

## Estado por fases

Fases 0–7 registradas por el estudiante. La Fase 8 añade revisión de calidad, regresiones y manuales Markdown/PDF. Se verificaron 45 pruebas Java y 7 del frontend, además del build.

La revisión visual local, capturas reales, dos recorridos en proyector y empaquetado siguen pendientes de Fase 9: el entorno del agente impide abrir puertos y lanzar navegador. No se presenta esa revisión como realizada.

El comparador conserva su aislamiento y resultados de referencia para 42/300/4. El escenario ahora utiliza una barrera; al resolver, activa prevención para reintentar sin recrear el ciclo, y **Restaurar recursos** retira mantenimiento. Los cambios se dejan sin `git add`, commit ni push, por instrucción del estudiante.

## Estructura

```text
proyecto_final/
├── PLAN_AGENTE.md
└── Proyecto_SOPES_2/
    ├── backend/             # Java, API y pruebas
    ├── frontend/            # React, transporte y build
    ├── docs/                # Manuales, PDF, diagramas y aceptación
    └── scripts/             # Generación local de documentos
```

La cola pendiente puede crecer si entran pedidos indefinidamente sin stock. Pausar el generador y reabastecer evita acumular trabajo sin factibilidad. Los límites y supuestos se detallan en el Manual Técnico.
