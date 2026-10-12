package cl.duoc.banco.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Microservicio de procesos batch del Banco XYZ.
 * Reemplaza los procesos COBOL + Shell del mainframe por tres jobs de Spring Batch:
 * <ol>
 *     <li>reporteTransaccionesDiariasJob</li>
 *     <li>calculoInteresesMensualesJob</li>
 *     <li>estadosCuentaAnualesJob</li>
 * </ol>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BatchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BatchServiceApplication.class, args);
    }
}
