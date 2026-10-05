#!/usr/bin/env bash
# Recorrido completo de pruebas. Uso: ./scripts/demo.sh   (requiere curl y el stack levantado)
GW=http://localhost:8080
AUTH=http://localhost:9000/oauth2/token

token() { curl -s -u "$1" -d grant_type=client_credentials -d "scope=$2" "$AUTH" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'; }
step() { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }

step "1. Sin token (esperado 401)"
curl -s -o /dev/null -w "HTTP %{http_code}\n" $GW/web/accounts/101/summary

WEB=$(token web-client:web-secret "web bank.read")
MOB=$(token mobile-client:mobile-secret "mobile bank.read")
ADM=$(token admin-client:admin-secret "web mobile bank.read bank.write")

step "2. Canal WEB con token web (200, respuesta completa)"
curl -s -H "Authorization: Bearer $WEB" $GW/web/accounts/101/summary | head -c 700; echo " ..."

step "3. Canal MOVIL con token mobile (200, respuesta liviana)"
curl -s -H "Authorization: Bearer $MOB" $GW/mobile/accounts/101/summary; echo

step "4. Token web en canal movil (esperado 403)"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $WEB" $GW/mobile/accounts/101/summary

step "5. Token de solo lectura intenta escribir (esperado 403)"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST $GW/api/transactions -H "Authorization: Bearer $WEB" \
  -H "Content-Type: application/json" -d '{"cuentaId":101,"tipo":"deposito","monto":1000}'

step "6. Registrar transaccion con admin-client (201) -> evento Kafka"
curl -s -X POST $GW/api/transactions -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
  -d '{"cuentaId":101,"tipo":"deposito","monto":25000,"descripcion":"Deposito de prueba"}'; echo
sleep 3

step "7. Notificaciones generadas por el consumidor Kafka"
curl -s -H "Authorization: Bearer $ADM" $GW/api/notifications; echo

step "8. Cuenta inexistente (esperado 404)"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $WEB" $GW/web/accounts/999/summary

echo
echo "Para probar la resiliencia:  docker stop bank-accounts-service  y repetir el paso 2 (respuesta con degraded:true)"
