# Matriz de aceptación — Fase 8

Fecha: 22/10/2026. Estado de implementación y evidencia automatizada; no sustituye el recorrido visual pendiente. No se marca el proyecto como listo para entrega sólo por compilar.

| Requisito | Evidencia | Estado |
|---|---|---|
| R1 Cuenta y nivel 1–5 | GestorClientes, ClienteDTO, Gestión Manual; Fase6ApiTest | Implementado y probado por API |
| R2 Pedido, líneas, cantidades y estado | Pedido/LineaPedido; Fase1ModeloTest | Implementado; columnas de la UI en recorrido visual |
| R3 Generación indefinida de todos los niveles | GeneradorPedidos y Fase8CalidadTest | Sin tope acumulado; cola y memoria siguen siendo finitas en la máquina |
| R4 Pedido manual en cualquier momento | POST /api/pedidos y PanelRegistroManual | Implementado y probado por API |
| R5 Políticas intercambiables y comparables | Fase3PoliticasYEstadisticasTest y suites F7 | Cuatro políticas; comparación completa reproducible |
| R6 Trabajo proporcional | TrabajadorPedido y casos manuales del comparador | Unidades × 30 s simulados |
| R7 Tipos y recursos distintos | Catálogo y prueba de combinaciones F1 | Seis tipos y dieciocho productos |
| R8 Contención real de recursos | Semáforos; escenarios F5/F8 y concurrencia F2/F7 | Implementado con permisos reales |
| R9 Falta de recurso vuelve a espera | Despachador y Fase2ConcurrenciaTest | Rollback de recurso/stock en prevención; adquisición incremental reservada para demo |
| R10 Mensajes y productor más rápido | Fase4AlmacenEInventarioTest | Cola 20 y contrapresión sin descarte en operación normal |
| R11 Catálogo fijo desde UI | CatalogoProductos, GET /api/catalogo | Implementado y probado por API |
| R12 Llegada despierta pedidos | Fase4AlmacenEInventarioTest | Flujo automático recepción → inventario → despacho |
| R13 No contigüidad y espacio | Fase4AlmacenEInventarioTest y Fase6ApiTest | Bitmap, lotes, liberación y reutilización |
| R14 Provocación e informe | Detector matricial, barrera; suites F5/F6/F8 | Bloqueo real comprobado; presentación visual pendiente |
| R15 Elección manual de ganador | Pruebas F5/F6/F8 eligen ambos ganadores | Retroceso y finalización de ambos sin fuga de permisos |
| R16 Paneles en tiempo real | REST/SSE, sondeo, Recharts y once paneles | Implementado; inspección de navegador, proyector y capturas pendientes |
| R17 Java, sincronización y errores | 45 pruebas Java, trazas, excepciones de dominio y comentarios en español | Revisado y probado; supuestos y limitaciones documentados |
| R18 Fuente y manuales | manual_usuario.md/.pdf y manual_tecnico.md/.pdf | Documentos generados; capturas reales y cierre visual pendientes |

## Comprobación ejecutada

- `mvn test`: 45 ejecuciones, cero fallos, errores u omisiones.
- `npm test`: 7 pruebas aprobadas.
- `npm run build`: TypeScript/Vite termina; aviso no bloqueante por tamaño del bundle.
- `python scripts/generar_manuales.py`: dos PDF A4 con diagramas vectoriales.
- `pdfinfo` y `pdftotext`: documentos reconocidos y texto extraíble; páginas renderizadas revisadas.
- `git diff --check`: sin errores de espacios. No se prepara el índice ni se crea commit.
- `mvn package`: compiló y volvió a ejecutar las 45 pruebas con éxito; después falló en empaquetado por la dependencia de plugin `org.apache.maven.shared:file-management:3.1.0`, ausente de la caché. El entorno deniega escritura en `~/.m2`. No se generó ni se afirma un JAR entregable desde este entorno.

## Cierre pendiente de Fase 9

Usar docs/verificacion_fase6.md, docs/verificacion_fase7.md y docs/capturas/README.md. Ejecutar dos veces el guion de demostración completo en navegador, revisar legibilidad de proyector, incorporar las capturas en el manual, regenerar PDF y empaquetar la entrega. La repetición automatizada de deadlock no se presenta como dos demos visuales realizadas.
