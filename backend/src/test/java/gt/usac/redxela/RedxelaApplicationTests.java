package gt.usac.redxela;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de humo inicial para verificar que el contexto de Spring Boot
 * levanta correctamente con Java 21.
 */
@SpringBootTest
class RedxelaApplicationTests {

    @Test
    @DisplayName("El contexto de la aplicación Spring Boot debe cargar exitosamente")
    void contextLoads() {
        assertTrue(true, "Contexto cargado exitosamente");
    }
}
