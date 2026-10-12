package cl.duoc.banco.customers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RutTest {

    @Test
    void validaDigitoVerificador() {
        assertThat(Rut.normalizar("11.111.111-1")).isEqualTo("11111111-1");
        assertThat(Rut.normalizar("12345678-5")).isEqualTo("12345678-5");
        assertThat(Rut.normalizar("12345678-9")).isNull();
        assertThat(Rut.normalizar("abc")).isNull();
    }

    @Test
    void formaRutValido() {
        assertThat(Rut.normalizar(Rut.formar(15_000_000))).isNotNull();
    }
}
