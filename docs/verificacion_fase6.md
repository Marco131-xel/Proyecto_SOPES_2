# Fase 6: estado y verificación

La API REST, SSE y los paneles operativos de React están implementados. Falta revisar visualmente la aplicación conectada en un navegador local. El comparador se implementó posteriormente en la Fase 7; consulta [su guía de verificación](verificacion_fase7.md).

## Verificación automatizada

Desde `Proyecto_SOPES_2/backend`:

```bash
mvn test
```

Desde `Proyecto_SOPES_2/frontend` (Node 22 para las pruebas de temporizadores):

```bash
npm test
npm run build
```

Se verificaron 31 pruebas Java y 3 de transporte del frontend. MockMvc usa el controlador, serialización y motor reales sin abrir puertos. Las pruebas del frontend simulan el transporte; no sustituyen un recorrido visual.

## Recorrido pendiente en navegador

Abrir dos terminales desde `Proyecto_SOPES_2`:

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

Visitar `http://localhost:5173`. Los datos viven en memoria y se reinician con el backend. El arranque incorpora dos ubicaciones por producto a través de Recepción e Inventario; esperar a que termine esa carga antes de probar pedidos.

- [ ] Operaciones: ver seis tipos de recursos y sus 26 unidades; iniciar y pausar el generador; cambiar tasa, política y velocidad (1–600×).
- [ ] Pausar el reloj: comprobar que el trabajo en curso deja de avanzar; reanudarlo.
- [ ] Crear un cliente con uno de los cinco niveles; comprobar que aparece con su nivel y nombre correctos.
- [ ] Registrar un pedido con varias líneas; comprobar cola, criterio de orden, motivo de espera, progreso y finalización en el registro.
- [ ] Almacén: recorrer seis pasillos, seleccionar una ubicación, comprobar producto y lote, tabla de asignación, cantidades actuales y fragmentación.
- [ ] Recepción: registrar un producto del catálogo y cantidad; comprobar patio, cola y aumento automático de existencias. Un pedido esperando ese stock debe reactivarse.
- [ ] Métricas: revisar tiempos por nivel, SLA, ocupación instantánea y serie de cola. El mantenimiento se distingue de trabajo productivo en la gráfica.
- [ ] Deadlock: con el generador pausado y sin pedidos pendientes/activos, pulsar «Provocar Deadlock». Se necesitan 2 unidades de `PROD-PES-01` y 1 de `PROD-VOL-01`. Esperar la alerta y el grafo; elegir ganador; comprobar retroceso de la víctima y terminación de ambos. Activar prevención para restaurar también las unidades en mantenimiento.
- [ ] Desconectar el stream desde las herramientas del navegador: comprobar actualización por sondeo y reconexión; detener el backend para verificar el aviso de datos desactualizados.
- [ ] Enviar datos inválidos: comprobar que el mensaje de error aparece en pantalla.
- [ ] Confirmar legibilidad a resolución de proyector y ausencia de errores en consola al recorrer todas las pestañas.

Los cambios se dejaron sin commit por instrucción del estudiante. La Fase 7 se continuó como una fase separada.
