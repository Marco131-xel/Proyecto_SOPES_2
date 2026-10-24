# Fase 9 — cierre de entrega y defensa

Estado: **preparación realizada; cierre visual y JAR pendientes**. Se retomó el commit del estudiante `f68e3b5`. No se ejecutan git add, commit ni push desde el agente.

## Alcance comprobable

Se prepara un ZIP de fuentes, manuales PDF/Markdown, diagramas, pruebas y frontend compilado, con lista permitida de archivos, estado de contenido y hashes SHA-256. No se incluyen .git, node_modules, cachés, configuración del agente ni credenciales. El paquete no se etiqueta como binario ejecutable si no contiene un JAR Spring Boot válido.

El script `scripts/preparar_entrega.py` valida informes JUnit sin fallos u omisiones, integridad básica de los PDF, recursos referenciados por el frontend, CRC del ZIP y hashes individuales. No inicia servidores, no sustituye npm test ni certifica pantallas. Sólo incluye un JAR si reconoce lanzador, Start-Class, clases y dependencias BOOT-INF. `--exigir-jar` impide producir un paquete sin él.

## Límites observados en este entorno

- Vite no puede escuchar en 127.0.0.1:5173: `listen EPERM: operation not permitted`. No fue posible conectar ambos servidores y recorrer la UI.
- La habilidad Playwright está instalada. Su wrapper no tiene el metadato npm en caché offline; se comprobó la CLI existente en la caché, pero al abrir sesión falla con EROFS al crear el registro de su daemon bajo `~/.cache/ms-playwright`. No se obtuvo navegador, snapshot ni captura.
- `mvn -o -DskipTests package` compila, pero no resuelve dependencias de maven-jar-plugin 3.4.2 ausentes de la caché: file-management 3.1.0, plexus-utils 4.0.1, commons-io 2.16.1 y otras. No se cambió el pom ni se fabricó un JAR de clases sin dependencias para aparentar un ejecutable.

No hay dos demos visuales realizadas ni capturas reales. Los dos recorridos se registrarán en `docs/guion_demo.md`. No se sustituyen por pruebas MockMvc, diagramas ni imágenes inventadas.

## Preparar la entrega en una máquina con permisos normales

Desde `Proyecto_SOPES_2/backend`:

```bash
mvn package
```

Maven necesita una caché escribible y acceso a sus dependencias si aún no están descargadas. Si `~/.m2` es de sólo lectura en esa máquina, usar una ruta local escribible sin cambiar la configuración global:

```bash
mvn -Dmaven.repo.local=target/cache-maven package
```

Esa caché empezará vacía y puede requerir descargar todas las dependencias; no basta cambiar la ruta sin disponer de red.

Desde `Proyecto_SOPES_2/frontend`:

```bash
npm ci
npm test
npm run build
```

Desde la raíz del repositorio:

```bash
python scripts/generar_manuales.py
python scripts/preparar_entrega.py --exigir-jar
```

El ZIP y su hash quedan en `output/entrega/`. Sin JAR, ejecutar el script sin esa opción produce un paquete identificado explícitamente como fuentes sin JAR. El árbol extraído se llama `REDXela`; el JAR, si está, queda en `REDXela/backend/redxela-1.0.0.jar`.

## Ejecutar y verificar el paquete extraído

1. Extraer en una carpeta distinta y revisar `ESTADO_ENTREGA.json` y `SHA256SUMS.txt`.
2. Desde el directorio que contiene la carpeta REDXela, ejecutar `sha256sum -c REDXela/SHA256SUMS.txt`. El manifiesto usa rutas con ese prefijo y no se incluye a sí mismo en sus hashes.
3. Iniciar `java -jar backend/redxela-1.0.0.jar` si está disponible; en paquete de fuentes, usar `cd backend` y `mvn spring-boot:run`.
4. Iniciar el frontend con `npm ci` y `npm run dev` desde frontend; abrir localhost:5173. El build dist se incluye, pero fuera de Vite se requiere un servidor que dirija `/api` al backend. No se debe abrir index.html con doble clic.
5. Ejecutar el guion dos veces, incorporar las capturas, revisar los manuales y volver a generar el ZIP definitivo.

La fase se cierra únicamente cuando el JAR, las capturas y los dos recorridos estén comprobados. La revisión física en proyector debe anotarla quien la realice.

## Evidencia automatizada de esta preparación

- `mvn -o test`: 45 pruebas aprobadas, sin errores ni omisiones.
- `npm test`: 7 pruebas aprobadas; `npm run build` termina con el aviso ya conocido de tamaño del bundle.
- El modo `--exigir-jar` rechaza correctamente la ausencia de ejecutable.
- Se generó un paquete de 137 entradas con fuentes, configuración pública, pruebas, manuales y frontend, sin JAR.
- Se extrajo en una carpeta temporal distinta y `sha256sum -c REDXela/SHA256SUMS.txt` verificó todas las entradas.
- Dos empaquetados consecutivos con las mismas entradas produjeron exactamente el mismo SHA-256. El hash del archivo actual está al lado del ZIP, en su archivo .sha256; no se fija aquí porque actualizar documentación cambia el paquete.
- `git diff --check` no informa errores; el índice sigue vacío y HEAD permanece en el commit del estudiante.

Estas comprobaciones validan construcción de fuentes, pruebas y transporte del paquete, no la ejecución visual completa ni el arranque de un JAR que no se pudo producir.
