# Funciones comunes del proceso legacy (reconstruido en awk a partir de las reglas de negocio del Banco XYZ).
function trim(s) { gsub(/^[ \t\r]+|[ \t\r]+$/, "", s); return s }
function bisiesto(a) { return (a % 4 == 0 && a % 100 != 0) || a % 400 == 0 }
function fecha_valida(a, m, d,   dm) {
    if (m < 1 || m > 12 || d < 1) return 0
    split("31 28 31 30 31 30 31 31 30 31 30 31", dm, " ")
    if (m == 2 && bisiesto(a)) return d <= 29
    return d <= dm[m]
}
# Devuelve la fecha en formato ISO (aaaa-mm-dd) o "" si es invalida. Acepta 4 formatos.
function normalizar_fecha(f,   p, a, m, d) {
    f = trim(f)
    if (f ~ /^[0-9][0-9][0-9][0-9][-\/][0-9][0-9][-\/][0-9][0-9]$/) {
        if (substr(f,5,1) != substr(f,8,1)) return ""
        a = substr(f,1,4)+0; m = substr(f,6,2)+0; d = substr(f,9,2)+0
    } else if (f ~ /^[0-9][0-9][-\/][0-9][0-9][-\/][0-9][0-9][0-9][0-9]$/) {
        if (substr(f,3,1) != substr(f,6,1)) return ""
        d = substr(f,1,2)+0; m = substr(f,4,2)+0; a = substr(f,7,4)+0
    } else return ""
    if (!fecha_valida(a, m, d)) return ""
    return sprintf("%04d-%02d-%02d", a, m, d)
}
function es_numero(s) { return s ~ /^-?[0-9]+(\.[0-9]+)?$/ }
function es_entero(s) { return s ~ /^-?[0-9]+$/ }
# Redondeo HALF_UP (equivalente a ROUNDED de COBOL)
function redondear(x, dec,   f) { f = 10 ^ dec; return (x >= 0 ? int(x * f + 0.5 + 1e-9) : -int(-x * f + 0.5 + 1e-9)) / f }
