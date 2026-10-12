package gt.usac.redxela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de inicio para la aplicación REDXela.
 * 
 * Configura el arranque de Spring Boot para exponer la API REST y SSE.
 * Toda la lógica de concurrencia y sincronización del almacén y centro de distribución
 * se implementa utilizando primitivas nativas de java.util.concurrent de forma independiente.
 */
@SpringBootApplication
public class RedxelaApplication {

    public static void main(String[] args) {
        // Configuramos el manejador por defecto de excepciones no capturadas
        // para garantizar que ningún hilo muera en silencio sin dejar traza en los logs.
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.printf("[FATAL] Excepción no controlada en el hilo '%s': %s%n",
                    thread.getName(), throwable.getMessage());
            throwable.printStackTrace(System.err);
        });

        SpringApplication.run(RedxelaApplication.class, args);
    }
}
