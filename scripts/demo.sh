#!/usr/bin/env bash
# =====================================================================================
# Recorrido completo de pruebas del Banco XYZ (EFT).  Uso:  ./scripts/demo.sh
# Requisitos: curl y el stack levantado (docker compose up --build -d).
# -k: el certificado del Gateway es autofirmado (en AWS se usa un certificado de ACM).
# =====================================================================================
GW=${GW:-https://localhost:8443}
C="curl -sk"
token() { $C -u "$1" -d grant_type=client_credentials -d "scope=$2" "$GW/oauth2/token" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'; }
step()  { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }
code()  { $C -o /dev/null -w "HTTP %{http_code}\n" "$@"; }

step "0. Tokens por canal (emitidos por el auth-server A TRAVES del Gateway HTTPS)"
WEB=$(token web-client:web-secret "web bank.read bank.write")
MOB=$(token mobile-client:mobile-secret "mobile bank.read")
ATM=$(token atm-client:atm-secret "atm bank.read bank.write")
ADM=$(token admin-client:admin-secret "web mobile atm bank.read bank.write batch.admin")
echo "web=${WEB:0:25}...  mobile=${MOB:0:25}...  atm=${ATM:0:25}...  admin=${ADM:0:25}..."

step "1. Seguridad: sin token -> 401 | token web en canal movil -> 403 | movil intentando escribir -> 403"
code $GW/web/accounts/101/summary
code -H "Authorization: Bearer $WEB" $GW/mobile/accounts/101/summary
code -X POST -H "Authorization: Bearer $MOB" -H "Content-Type: application/json" $GW/api/payments/deposits -d '{"cuentaId":101,"monto":1000}'

step "2. PARTE 2 - BFF WEB (respuesta completa: perfil + cuentas + movimientos)"
$C -H "Authorization: Bearer $WEB" $GW/web/customers/1/dashboard | head -c 900; echo " ..."

step "3. PARTE 2 - BFF MOVIL (respuesta liviana, 5 movimientos, gzip + ETag)"
$C -H "Authorization: Bearer $MOB" $GW/mobile/accounts/101/summary; echo
$C -D - -o /dev/null -H "Accept-Encoding: gzip" -H "Authorization: Bearer $MOB" $GW/mobile/accounts/101/summary | grep -iE "etag|content-encoding|cache-control"

step "4. PARTE 2 - BFF CAJEROS: terminal no registrado -> 403 | saldo -> 200 | retiro idempotente"
code -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-999" $GW/atm/accounts/101/balance
$C -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" $GW/atm/accounts/101/balance; echo
KEY="atm-$(date +%s)"
$C -X POST $GW/atm/withdrawals -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" -H "Idempotency-Key: $KEY" \
   -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":20000}'; echo
echo "(mismo Idempotency-Key otra vez: NO debita dos veces)"
$C -X POST $GW/atm/withdrawals -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" -H "Idempotency-Key: $KEY" \
   -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":20000}'; echo
echo "(monto que no es multiplo de 5.000 -> 400)"
code -X POST $GW/atm/withdrawals -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" -H "Idempotency-Key: x-$KEY" \
   -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":12345}'

step "5. PARTE 3 - Clientes y Cuentas: crear cliente, abrir cuenta, depositar, transferir"
$C -X POST $GW/api/customers -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
   -d '{"rut":"11.111.111-1","nombre":"Maria Perez","email":"maria@correo-ejemplo.cl","telefono":"+56 9 8765 4321","segmento":"PERSONA"}'; echo
CLI=$($C -H "Authorization: Bearer $ADM" $GW/api/customers/rut/11111111-1 | sed -E 's/.*"id":([0-9]+).*/\1/')
NUEVA=$($C -X POST $GW/api/accounts -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
   -d "{\"clienteId\":$CLI,\"tipo\":\"ahorro\",\"depositoInicial\":0}" | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "Cliente $CLI - cuenta nueva $NUEVA"
$C -X POST $GW/api/payments/deposits -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
   -d "{\"cuentaId\":$NUEVA,\"monto\":150000,\"descripcion\":\"Deposito inicial\"}"; echo
$C -X POST $GW/web/transfers -H "Authorization: Bearer $WEB" -H "Content-Type: application/json" \
   -d "{\"cuentaOrigen\":$NUEVA,\"cuentaDestino\":102,\"monto\":50000,\"descripcion\":\"Pago arriendo\"}"; echo
echo "(transferencia sin saldo suficiente -> 422 RECHAZADA)"
$C -X POST $GW/web/transfers -H "Authorization: Bearer $WEB" -H "Content-Type: application/json" \
   -d "{\"cuentaOrigen\":$NUEVA,\"cuentaDestino\":102,\"monto\":99999999,\"descripcion\":\"sin saldo\"}"; echo

step "6. PARTE 3 - Eventos Kafka: operacion de monto alto -> alerta de seguridad"
$C -X POST $GW/api/payments/deposits -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
   -d '{"cuentaId":103,"monto":2500000,"descripcion":"Deposito grande"}' > /dev/null
sleep 3
$C -H "Authorization: Bearer $ADM" "$GW/api/notifications?categoria=SEGURIDAD"; echo
$C -H "Authorization: Bearer $ADM" "$GW/api/notifications?categoria=TRANSACCION" | head -c 500; echo " ..."

step "7. PARTE 1 - Spring Batch: ejecuciones y resultados"
$C -H "Authorization: Bearer $ADM" $GW/api/batch/jobs | head -c 1200; echo " ..."
$C -H "Authorization: Bearer $ADM" "$GW/api/batch/reports/intereses?periodo=2024-12&cuentaId=101"; echo
$C -H "Authorization: Bearer $ADM" "$GW/api/batch/reports/rechazos?job=INTERESES&clave=2024-12"; echo

step "8. PARTE 1 - Fallo critico + reejecucion automatica (ver: docker compose logs -f batch-service)"
$C -X POST -H "Authorization: Bearer $ADM" \
   "$GW/api/batch/jobs/reporteTransaccionesDiariasJob/executions?fechaProceso=2024-12-31&simularFalloCriticoEnLinea=450&forzar=true" | head -c 400; echo " ..."
echo "El job queda FAILED y en ~20 s se reinicia solo y termina COMPLETED. Consultar:"
echo "  curl -sk -H \"Authorization: Bearer \$ADM\" $GW/api/batch/jobs/reporteTransaccionesDiariasJob/executions"

echo
echo "Resiliencia: docker compose stop accounts-service  y repetir el paso 2 (respuesta con degraded:true)"
