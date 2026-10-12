package gt.usac.redxela.modelo;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Almacén en memoria concurrente para la administración de clientes (R1).
 * 
 * Es thread-safe utilizando ConcurrentHashMap. Inicializa un conjunto de clientes
 * semilla para todos los niveles de servicio y permite el registro dinámico
 * de nuevas cuentas tanto por la UI como por el generador de pedidos.
 */
@Component
public class GestorClientes {

    private final Map<String, Cliente> clientes = new ConcurrentHashMap<>();
    private final AtomicInteger secuencial = new AtomicInteger(100);

    public GestorClientes() {
        // Carga de clientes semilla para cubrir los 5 niveles de servicio
        registrarClienteDirecto(new Cliente("CLI-001", "Hospital Regional Xela", "farmacia@hospitalxela.gt", NivelServicio.EXPRES));
        registrarClienteDirecto(new Cliente("CLI-002", "Farmacias La Salud", "compras@lasalud.gt", NivelServicio.EXPRES));
        registrarClienteDirecto(new Cliente("CLI-003", "Supermercados El Sol", "logistica@elsol.gt", NivelServicio.PRIORITARIO));
        registrarClienteDirecto(new Cliente("CLI-004", "Tecnología Los Altos", "pedidos@losaltostec.gt", NivelServicio.PRIORITARIO));
        registrarClienteDirecto(new Cliente("CLI-005", "Distribuidora Central", "operaciones@central.gt", NivelServicio.ESTANDAR));
        registrarClienteDirecto(new Cliente("CLI-006", "Comercializadora Quetzal", "contacto@quetzal.gt", NivelServicio.ESTANDAR));
        registrarClienteDirecto(new Cliente("CLI-007", "Ferretería El Tornillo", "ventas@eltornillo.gt", NivelServicio.PROGRAMADO));
        registrarClienteDirecto(new Cliente("CLI-008", "Mueblería y Diseños", "diseno@mueblesxela.gt", NivelServicio.PROGRAMADO));
        registrarClienteDirecto(new Cliente("CLI-009", "Librería y Papelería Sol", "libros@libreriasol.gt", NivelServicio.ECONOMICO));
        registrarClienteDirecto(new Cliente("CLI-010", "Bazar y Novedades", "bazar@novedades.gt", NivelServicio.ECONOMICO));
    }

    private void registrarClienteDirecto(Cliente cliente) {
        clientes.put(cliente.id(), cliente);
    }

    /**
     * Registra un nuevo cliente generando automáticamente un ID único correlativo.
     * 
     * @param nombre Nombre de la empresa o cliente.
     * @param correo Correo electrónico.
     * @param nivel Nivel de servicio contratado (1 a 5).
     * @return El Cliente registrado.
     */
    public Cliente registrarCliente(String nombre, String correo, NivelServicio nivel) {
        String id = "CLI-" + secuencial.incrementAndGet();
        Cliente nuevo = new Cliente(id, nombre, correo, nivel);
        clientes.put(id, nuevo);
        return nuevo;
    }

    public Optional<Cliente> buscarPorId(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(clientes.get(id));
    }

    public Cliente obtenerPorId(String id) {
        return buscarPorId(id)
                .orElseThrow(() -> new NoSuchElementException("Cliente no encontrado con ID: " + id));
    }

    public List<Cliente> obtenerTodos() {
        return new ArrayList<>(clientes.values());
    }

    /**
     * Selecciona un cliente aleatorio para la generación automática de pedidos.
     * 
     * @param random Instancia generadora de números pseudoaleatorios.
     * @return Un Cliente seleccionado al azar.
     */
    public Cliente obtenerAleatorio(Random random) {
        List<Cliente> lista = obtenerTodos();
        if (lista.isEmpty()) {
            throw new IllegalStateException("No hay clientes registrados en el sistema");
        }
        return lista.get(random.nextInt(lista.size()));
    }
}
