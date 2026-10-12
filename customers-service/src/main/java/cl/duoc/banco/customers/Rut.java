package cl.duoc.banco.customers;

/** Validacion y formato del RUT chileno (modulo 11). */
public final class Rut {

    private Rut() {
    }

    /** "12.345.678-5" o "12345678-5" -> "12345678-5" ; null si es invalido. */
    public static String normalizar(String rut) {
        if (rut == null) {
            return null;
        }
        String limpio = rut.replace(".", "").replace(" ", "").toUpperCase();
        if (!limpio.matches("\\d{7,8}-[\\dK]")) {
            return null;
        }
        String cuerpo = limpio.substring(0, limpio.indexOf('-'));
        char dv = limpio.charAt(limpio.length() - 1);
        return dv == digitoVerificador(Integer.parseInt(cuerpo)) ? limpio : null;
    }

    public static char digitoVerificador(int cuerpo) {
        int suma = 0;
        int factor = 2;
        for (int n = cuerpo; n > 0; n /= 10) {
            suma += (n % 10) * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        return resto == 11 ? '0' : resto == 10 ? 'K' : (char) ('0' + resto);
    }

    public static String formar(int cuerpo) {
        return cuerpo + "-" + digitoVerificador(cuerpo);
    }
}
