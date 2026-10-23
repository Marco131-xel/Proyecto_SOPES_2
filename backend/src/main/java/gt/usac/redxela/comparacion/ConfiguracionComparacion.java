package gt.usac.redxela.comparacion;

/** Carga finita del experimento: los límites acotan memoria e hilos acumulados. */
public record ConfiguracionComparacion(long semilla, int cantidadPedidos, double tasaLlegadasPorMinuto) {
    public static final int MAX_PEDIDOS = 1000;

    public ConfiguracionComparacion {
        if (cantidadPedidos < 1 || cantidadPedidos > MAX_PEDIDOS)
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + MAX_PEDIDOS);
        if (!Double.isFinite(tasaLlegadasPorMinuto) || tasaLlegadasPorMinuto < 0.1 || tasaLlegadasPorMinuto > 120)
            throw new IllegalArgumentException("La tasa debe estar entre 0.1 y 120 pedidos/min simulado");
    }

    public static ConfiguracionComparacion porDefecto() {
        return new ConfiguracionComparacion(42, 300, 4);
    }
}
