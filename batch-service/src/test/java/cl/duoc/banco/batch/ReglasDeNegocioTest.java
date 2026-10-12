package cl.duoc.banco.batch;

import cl.duoc.banco.batch.domain.CalculadoraIntereses;
import cl.duoc.banco.batch.domain.CuentaInteres;
import cl.duoc.banco.batch.domain.InteresMensual;
import cl.duoc.banco.batch.domain.LectorCsv;
import cl.duoc.banco.batch.domain.Motivo;
import cl.duoc.banco.batch.domain.Procesado;
import cl.duoc.banco.batch.domain.RegistroCsv;
import cl.duoc.banco.batch.domain.ReglasLegacy;
import cl.duoc.banco.batch.domain.TransaccionDiaria;
import cl.duoc.banco.batch.domain.ValidadorCuenta;
import cl.duoc.banco.batch.domain.ValidadorMovimientoAnual;
import cl.duoc.banco.batch.domain.ValidadorMovimientoDiario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias de las reglas migradas desde el legacy (sin Spring). */
class ReglasDeNegocioTest {

    private static RegistroCsv linea(int n, String texto) {
        return new RegistroCsv(n, texto, LectorCsv.separar(texto));
    }

    @Test
    void aceptaLosCuatroFormatosDeFechaYRechazaFechasImposibles() {
        LocalDate esperada = LocalDate.of(2024, 3, 18);
        assertThat(ReglasLegacy.parsearFecha("2024-03-18")).contains(esperada);
        assertThat(ReglasLegacy.parsearFecha("18-03-2024")).contains(esperada);
        assertThat(ReglasLegacy.parsearFecha("2024/03/18")).contains(esperada);
        assertThat(ReglasLegacy.parsearFecha("18/03/2024")).contains(esperada);
        assertThat(ReglasLegacy.parsearFecha("31-02-2024")).isEmpty();
        assertThat(ReglasLegacy.parsearFecha("12-25-2024")).isEmpty();
    }

    @Test
    void normalizaTiposConTilde() {
        assertThat(ReglasLegacy.normalizarTipo(" Depósito ")).isEqualTo("deposito");
    }

    @Test
    void reporteDiarioDetectaAnomalias() {
        ValidadorMovimientoDiario v = new ValidadorMovimientoDiario(new BigDecimal("2500"));
        Map<Long, Integer> vistos = new HashMap<>();

        Procesado<TransaccionDiaria> ok = v.validar(linea(2, "1,2024-01-01,1000,debito"), "2024-12-31", vistos);
        assertThat(ok.esValido()).isTrue();
        assertThat(ok.valor().alerta()).isNull();

        Procesado<TransaccionDiaria> alto = v.validar(linea(3, "2,2024-01-02,3000,credito"), "2024-12-31", vistos);
        assertThat(alto.esValido()).isTrue();
        assertThat(alto.valor().alerta()).isEqualTo(Motivo.MONTO_ALTO);

        assertThat(v.validar(linea(4, "3,2024-01-03,-200,debito"), "x", vistos).rechazo().motivo())
                .isEqualTo(Motivo.MONTO_NEGATIVO);
        assertThat(v.validar(linea(5, "4,2024-01-03,0,debito"), "x", vistos).rechazo().motivo())
                .isEqualTo(Motivo.MONTO_CERO);
        assertThat(v.validar(linea(6, "5,2024-01-03,,debito"), "x", vistos).rechazo().motivo())
                .isEqualTo(Motivo.MONTO_VACIO);
        assertThat(v.validar(linea(7, "6,2024-01-03,100,invalido"), "x", vistos).rechazo().motivo())
                .isEqualTo(Motivo.TIPO_INVALIDO);
        assertThat(v.validar(linea(8, "1,2024-01-04,100,debito"), "x", vistos).rechazo().motivo())
                .isEqualTo(Motivo.DUPLICADO);
    }

    @Test
    void reprocesarLaMismaLineaNoLaMarcaComoDuplicada() {
        // Garantiza resultados identicos cuando Spring Batch reintenta un chunk
        ValidadorMovimientoDiario v = new ValidadorMovimientoDiario(new BigDecimal("2500"));
        Map<Long, Integer> vistos = new HashMap<>();
        RegistroCsv r = linea(2, "1,2024-01-01,1000,debito");
        assertThat(v.validar(r, "x", vistos).esValido()).isTrue();
        assertThat(v.validar(r, "x", vistos).esValido()).isTrue();
    }

    @Test
    void cuentasConservaElPrimerRegistroValidoYDescartaDuplicados() {
        ValidadorCuenta v = new ValidadorCuenta();
        Map<Long, Integer> aceptadas = new HashMap<>();
        assertThat(v.validar(linea(2, "101,John Doe,,30,ahorro"), "p", aceptadas).rechazo().motivo())
                .isEqualTo(Motivo.SALDO_INVALIDO);
        assertThat(v.validar(linea(3, "101,John Doe,5000,30,ahorro"), "p", aceptadas).esValido()).isTrue();
        assertThat(v.validar(linea(4, "101,Jane,9000,40,prestamo"), "p", aceptadas).rechazo().motivo())
                .isEqualTo(Motivo.DUPLICADO);
        assertThat(v.validar(linea(5, "102,Unknown,9000,40,prestamo"), "p", aceptadas).rechazo().motivo())
                .isEqualTo(Motivo.NOMBRE_INVALIDO);
        assertThat(v.validar(linea(6, "103,Bob,9000,150,prestamo"), "p", aceptadas).rechazo().motivo())
                .isEqualTo(Motivo.EDAD_FUERA_RANGO);
        assertThat(v.validar(linea(7, "104,Bob,9000,40,-1"), "p", aceptadas).rechazo().motivo())
                .isEqualTo(Motivo.TIPO_INVALIDO);
    }

    @Test
    void calculaInteresConRedondeoComercial() {
        CalculadoraIntereses c = new CalculadoraIntereses(Map.of("ahorro", new BigDecimal("0.03"),
                "prestamo", new BigDecimal("0.18"), "hipoteca", new BigDecimal("0.042")));
        InteresMensual ahorro = c.calcular(new CuentaInteres("2024-12", 2, 101, "John", new BigDecimal("5000"), 30, "ahorro"));
        assertThat(ahorro.interes()).isEqualByComparingTo("12.50");
        assertThat(ahorro.naturaleza()).isEqualTo("ABONO");
        InteresMensual hipoteca = c.calcular(new CuentaInteres("2024-12", 3, 102, "Ana", new BigDecimal("7000"), 30, "hipoteca"));
        assertThat(hipoteca.interes()).isEqualByComparingTo("24.50");
        assertThat(hipoteca.saldoFinal()).isEqualByComparingTo("7024.50");
        assertThat(hipoteca.naturaleza()).isEqualTo("CARGO");
    }

    @Test
    void estadosAnualesCorrigenDescripcionYRechazanOtrosAnios() {
        ValidadorMovimientoAnual v = new ValidadorMovimientoAnual();
        Procesado<?> sinDesc = v.validar(linea(2, "101,2024/04/30,depósito,2500,"), 2024);
        assertThat(sinDesc.esValido()).isTrue();
        assertThat(v.validar(linea(3, "101,2023-04-30,compra,2500,x"), 2024).rechazo().motivo())
                .isEqualTo(Motivo.FUERA_DE_PERIODO);
        assertThat(v.validar(linea(4, "101,2024-04-30,compra,-500,x"), 2024).rechazo().motivo())
                .isEqualTo(Motivo.MONTO_NEGATIVO);
    }
}
