#!/usr/bin/env sh
# Calculo de Intereses Mensuales - version legacy (Shell + awk).
# Uso: ./intereses_mensuales_legacy.sh intereses_trimestrales.csv
# Tasas anuales: ahorro 3%, prestamo 18%, hipoteca 4.2%  -> tasa mensual = anual / 12
DIR=$(dirname "$0")
awk -F',' -f "$DIR/comun.awk" -f "$DIR/intereses_mensuales.awk" "$1" | sort
