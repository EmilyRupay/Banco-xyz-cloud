package cl.duoc.banco.bff.atm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.Set;

/**
 * @param terminales  cajeros autorizados (header X-ATM-ID)
 * @param montoMinimo monto minimo de retiro
 * @param montoMaximo monto maximo por operacion
 * @param multiplo    el cajero solo dispensa billetes: el monto debe ser multiplo de este valor
 */
@ConfigurationProperties(prefix = "bank.atm")
public record AtmProperties(Set<String> terminales,
                            @DefaultValue("5000") BigDecimal montoMinimo,
                            @DefaultValue("200000") BigDecimal montoMaximo,
                            @DefaultValue("5000") BigDecimal multiplo) {
}
