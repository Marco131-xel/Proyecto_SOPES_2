# Diagramas de documentación

Los SVG son ilustraciones vectoriales para Markdown, no capturas de una sesión. Cada SVG tiene una versión `.pic` con el mismo contenido lógico para que groff la incluya como vector en el PDF. Las posiciones o estilos pueden diferir entre pantalla e impresión.

- arquitectura: navegador, API, motor y comparación aislada.
- pedido: cola, reserva, recursos, trabajador y finalización.
- recepcion: productor, cola acotada, consumidor y almacén.
- deadlock: recurso retenido hacia pedido y pedido hacia recurso esperado.
- navegacion: organización de los paneles.

Si cambia el flujo, actualizar ambas representaciones y ejecutar `python scripts/generar_manuales.py`. Las capturas reales tienen su propia lista pendiente en `docs/capturas/README.md`.
