#!/usr/bin/env sh
# Reporte de Transacciones Diarias - version legacy (Shell + awk).
# Uso: ./reporte_diario_legacy.sh movimientos_financieros_diarios.csv [umbral_monto_alto]
DIR=$(dirname "$0")
awk -F',' -v UMBRAL="${2:-2500}" -f "$DIR/comun.awk" -f "$DIR/reporte_diario.awk" "$1" | sort
