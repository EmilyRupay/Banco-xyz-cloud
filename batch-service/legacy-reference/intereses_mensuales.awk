# Programa awk del proceso legacy. Se ejecuta desde intereses_mensuales_legacy.sh junto con comun.awk
BEGIN { tasa["ahorro"] = 0.03; tasa["prestamo"] = 0.18; tasa["hipoteca"] = 0.042 }
NR == 1 { next }
{
    leidos++
    id = trim($1); nombre = trim($2); saldo = trim($3); edad = trim($4); tipo = tolower(trim($5))
    if (!es_entero(id))                                 { rech["FORMATO_INVALIDO"]++; next }
    if (nombre == "" || tolower(nombre) == "unknown")   { rech["NOMBRE_INVALIDO"]++; next }
    if (!es_numero(saldo) || saldo + 0 <= 0)            { rech["SALDO_INVALIDO"]++; next }
    if (!es_entero(edad) || edad < 18 || edad > 100)    { rech["EDAD_FUERA_RANGO"]++; next }
    if (!(tipo in tasa))                                { rech["TIPO_INVALIDO"]++; next }
    if (id in aceptada)                                 { rech["DUPLICADO"]++; next }
    aceptada[id] = 1; cuentas++
    tm = redondear(tasa[tipo] / 12, 8)
    interes = redondear(saldo * tm, 2)
    total_interes[tipo] += interes; total_saldo[tipo] += saldo; n[tipo]++
    printf "CUENTA %s %s %.2f %.2f\n", id, tipo, saldo, interes > "/dev/stderr"
}
END {
    printf "LEIDOS=%d CUENTAS=%d\n", leidos, cuentas
    for (t in n) printf "TIPO %s cuentas=%d saldo=%.2f interes=%.2f\n", t, n[t], total_saldo[t], total_interes[t]
    for (m in rech) printf "RECHAZO %s=%d\n", m, rech[m]
}
