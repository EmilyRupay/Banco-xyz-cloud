# Programa awk del proceso legacy. Se ejecuta desde estados_anuales_legacy.sh junto con comun.awk
NR == 1 { next }
{
    leidos++
    id = trim($1); fecha = normalizar_fecha($2); tipo = tolower(trim($3)); monto = trim($4)
    gsub(/ó/, "o", tipo)
    if (!es_entero(id))                       { rech["FORMATO_INVALIDO"]++; next }
    if (fecha == "")                          { rech["FECHA_INVALIDA"]++; next }
    if (substr(fecha, 1, 4) != ANIO)          { rech["FUERA_DE_PERIODO"]++; next }
    if (tipo != "deposito" && tipo != "retiro" && tipo != "compra" && tipo != "pago") { rech["TIPO_INVALIDO"]++; next }
    if (!es_numero(monto))                    { rech["MONTO_VACIO"]++; next }
    if (monto + 0 < 0)                        { rech["MONTO_NEGATIVO"]++; next }
    if (monto + 0 == 0)                       { rech["MONTO_CERO"]++; next }
    validos++; cuentas[id] = 1; tot[tipo] += monto
    neto[id] += (tipo == "deposito" ? monto : -monto)
}
END {
    printf "LEIDOS=%d VALIDOS=%d CUENTAS=%d\n", leidos, validos, length(cuentas)
    for (t in tot) printf "TOTAL %s=%.2f\n", t, tot[t]
    for (m in rech) printf "RECHAZO %s=%d\n", m, rech[m]
    for (c in neto) printf "NETO cuenta %s=%.2f\n", c, neto[c]
}
