package cl.duoc.banco.batch.domain;

/** Catalogo de motivos de rechazo/alerta. Reproduce las validaciones que hacia el proceso legacy (COBOL + Shell). */
public enum Motivo {
    FORMATO_INVALIDO,
    FECHA_INVALIDA,
    FUERA_DE_PERIODO,
    TIPO_INVALIDO,
    MONTO_VACIO,
    MONTO_NEGATIVO,
    MONTO_CERO,
    MONTO_ALTO,
    DUPLICADO,
    SALDO_INVALIDO,
    EDAD_FUERA_RANGO,
    NOMBRE_INVALIDO
}
