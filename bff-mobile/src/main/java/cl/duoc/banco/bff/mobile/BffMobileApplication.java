package cl.duoc.banco.bff.mobile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Backend for Frontend del canal MOVIL: respuestas ligeras para ahorrar ancho de banda y bateria. */
@SpringBootApplication
public class BffMobileApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffMobileApplication.class, args);
    }
}
