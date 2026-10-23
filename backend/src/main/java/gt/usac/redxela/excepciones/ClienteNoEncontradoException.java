package gt.usac.redxela.excepciones;

/** Cuenta solicitada que no existe en el registro de clientes. */
public class ClienteNoEncontradoException extends java.util.NoSuchElementException {
    public ClienteNoEncontradoException(String mensaje) { super(mensaje); }
}
