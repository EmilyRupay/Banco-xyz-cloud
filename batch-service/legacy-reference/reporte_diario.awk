# Programa awk del proceso legacy. Se ejecuta desde reporte_diario_legacy.sh junto con comun.awk
NR == 1 { next }
{
    leidos++
    id = trim($1); fecha = normalizar_fecha($2); monto = trim($3); tipo = tolower(trim($4))
    if (!es_entero(id))                       { rech["FORMATO_INVALIDO"]++; next }
    if (id in visto)                          { rech["DUPLICADO"]++; next }
    visto[id] = 1
    if (fecha == "")                          { rech["FECHA_INVALIDA"]++; next }
    if (tipo != "debito" && tipo != "credito"){ rech["TIPO_INVALIDO"]++; next }
    if (!es_numero(monto))                    { rech["MONTO_VACIO"]++; next }
    if (monto + 0 < 0)                        { rech["MONTO_NEGATIVO"]++; next }
    if (monto + 0 == 0)                       { rech["MONTO_CERO"]++; next }
    validos++; cant[fecha]++
    if (tipo == "credito") cred[fecha] += monto; else deb[fecha] += monto
    if (monto + 0 > UMBRAL + 0) { alertas++; alf[fecha]++ }
    totcred += (tipo == "credito" ? monto : 0); totdeb += (tipo == "debito" ? monto : 0)
}
END {
    printf "LEIDOS=%d VALIDOS=%d ALERTAS_MONTO_ALTO=%d DIAS=%d\n", leidos, validos, alertas, length(cant)
    printf "TOTAL_CREDITOS=%.2f TOTAL_DEBITOS=%.2f NETO=%.2f\n", totcred, totdeb, totcred - totdeb
    for (m in rech) printf "RECHAZO %s=%d\n", m, rech[m]
}
