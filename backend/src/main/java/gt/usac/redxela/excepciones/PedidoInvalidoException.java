package gt.usac.redxela.excepciones;

/** Datos de pedido que violan las invariantes del dominio. */
public class PedidoInvalidoException extends IllegalArgumentException {
    public PedidoInvalidoException(String mensaje) { super(mensaje); }
}
