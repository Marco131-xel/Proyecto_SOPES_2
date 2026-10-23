# Fase 7 — comparador de políticas

Implementado: experimento aislado con hilos y semáforos reales, reloj lógico reproducible, carga con semilla, REST asíncrono, tabla global, barras agrupadas por nivel, desglose de recursos y conclusión de cada corrida. Detalles y resultados de referencia en [el borrador del Manual Técnico](manual_tecnico.md#7-comparación-experimental-de-políticas).

## Comprobaciones automatizadas

Desde `backend`: `mvn test`.

Desde `frontend`: `npm test` y `npm run build`.

Resultado: 39 pruebas Java y 5 del transporte frontend aprobadas. El build de Vite termina; conserva el aviso de tamaño del bundle, que no impide compilar.

## Recorrido visual pendiente

Iniciar `mvn spring-boot:run` en `backend` y `npm run dev` en `frontend`. Abrir `http://localhost:5173`, pestaña **Métricas y SLA**:

- [ ] Con semilla 42, 300 pedidos y tasa 4, pulsar **Comparar políticas**; ver estado de ejecución y cuatro filas de resultados.
- [ ] Repetir la configuración y comparar huella y métricas con la tabla del Manual Técnico.
- [ ] Cambiar semilla y comprobar una huella distinta.
- [ ] Cambiar la gráfica entre espera y SLA; revisar desglose por nivel y ocupación acumulada por recurso.
- [ ] Probar un pedido: niveles sin muestras deben indicarse como tales, sin barras que sugieran cumplimiento medido.
- [ ] Iniciar otra comparación, cambiar de pestaña y volver: recuperar resultado o trabajo en curso.
- [ ] Ejecutar el generador en vivo y comparar: la operación debe continuar de manera independiente.
- [ ] Desde otra pestaña intentar un segundo POST durante la ejecución: debe responder 409 sin crear una cola de trabajos.
- [ ] Interrumpir conexión durante la consulta, verificar error visible y recuperar con **Consultar estado**.

La Fase 8 queda pendiente: calidad general, manuales completos y demás entregables. No se realizaron commits ni `git add`.
