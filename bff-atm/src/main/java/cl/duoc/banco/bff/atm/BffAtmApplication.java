package cl.duoc.banco.bff.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Backend for Frontend de CAJEROS AUTOMATICOS: interfaz segura y eficiente para retiros y consultas de saldo. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BffAtmApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffAtmApplication.class, args);
    }
}
