package gt.usac.redxela.excepciones;

/** Resolución solicitada cuando el detector no encuentra un conflicto activo. */
public class ConflictoNoExisteException extends IllegalStateException {
    public ConflictoNoExisteException(String mensaje) { super(mensaje); }
}
