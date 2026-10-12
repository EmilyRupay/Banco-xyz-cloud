#!/usr/bin/env sh
# Generacion de Estados de Cuenta Anuales - version legacy (Shell + awk).
# Uso: ./estados_anuales_legacy.sh estados_financieros_anuales.csv 2024
DIR=$(dirname "$0")
awk -F',' -v ANIO="${2:-2024}" -f "$DIR/comun.awk" -f "$DIR/estados_anuales.awk" "$1" | sort
