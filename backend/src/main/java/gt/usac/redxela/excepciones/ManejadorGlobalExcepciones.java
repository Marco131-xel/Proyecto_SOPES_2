package gt.usac.redxela.excepciones;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Manejador global de excepciones para la API REST de REDXela (R17).
 * 
 * Intercepta fallos de validación, clientes no encontrados y conflictos
 * transformándolos en respuestas JSON estructuradas con códigos HTTP adecuados (400, 404, 409).
 */
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> manejarNoEncontrado(Exception ex) {
        return construirRespuesta(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarJsonInvalido(Exception ex) {
        return construirRespuesta(HttpStatus.BAD_REQUEST, "Solicitud inválida", "JSON ausente, mal formado o con tipos incorrectos");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> manejarIllegalArgument(IllegalArgumentException ex) {
        return construirRespuesta(HttpStatus.BAD_REQUEST, "Solicitud inválida", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> manejarIllegalState(IllegalStateException ex) {
        return construirRespuesta(HttpStatus.CONFLICT, "Estado del sistema en conflicto", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarRutaInexistente(Exception ex) {
        return construirRespuesta(HttpStatus.NOT_FOUND, "Ruta no encontrada", "No existe la ruta solicitada");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarMetodo(Exception ex) {
        return construirRespuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarTipoContenido(Exception ex) {
        return construirRespuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarGeneral(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Fallo no controlado en la API", ex);
        return construirRespuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", "Consulte el registro del backend");
    }

    private ResponseEntity<Map<String, Object>> construirRespuesta(HttpStatus status, String error, String mensaje) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", error,
                "mensaje", mensaje != null ? mensaje : "Error sin mensaje específico"
        ));
    }
}
