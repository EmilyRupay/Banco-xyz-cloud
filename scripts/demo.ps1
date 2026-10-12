# =====================================================================================
# Recorrido de pruebas del Banco XYZ para Windows PowerShell 5+/7.  Uso:  .\scripts\demo.ps1
# Usa curl.exe (incluido en Windows 10/11). -k: certificado autofirmado del Gateway.
# =====================================================================================
$GW = "https://localhost:8443"

function Token($cliente, $scopes) {
    $r = curl.exe -sk -u $cliente -d grant_type=client_credentials -d "scope=$scopes" "$GW/oauth2/token" | ConvertFrom-Json
    return $r.access_token
}
# El cuerpo JSON se pasa por archivo (@archivo): funciona igual en PowerShell 5 y 7
function Json($texto) { $f = New-TemporaryFile; Set-Content -Path $f -Value $texto -NoNewline; return "@$f" }
function Paso($t) { Write-Host "`n== $t" -ForegroundColor Cyan }
function Codigo { param([Parameter(ValueFromRemainingArguments = $true)] $a) curl.exe -sk -o NUL -w "HTTP %{http_code}`n" @a }

Paso "0. Tokens por canal (via Gateway HTTPS)"
$WEB = Token "web-client:web-secret" "web bank.read bank.write"
$MOB = Token "mobile-client:mobile-secret" "mobile bank.read"
$ATM = Token "atm-client:atm-secret" "atm bank.read bank.write"
$ADM = Token "admin-client:admin-secret" "web mobile atm bank.read bank.write batch.admin"
Write-Host "Tokens obtenidos: web, mobile, atm, admin"

Paso "1. Seguridad: 401 sin token | 403 token web en canal movil"
Codigo "$GW/web/accounts/101/summary"
Codigo -H "Authorization: Bearer $WEB" "$GW/mobile/accounts/101/summary"

Paso "2. BFF WEB (dashboard completo del cliente 1)"
curl.exe -sk -H "Authorization: Bearer $WEB" "$GW/web/customers/1/dashboard"

Paso "3. BFF MOVIL (respuesta liviana)"
curl.exe -sk -H "Authorization: Bearer $MOB" "$GW/mobile/accounts/101/summary"

Paso "4. BFF CAJEROS (terminal ATM-001)"
curl.exe -sk -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" "$GW/atm/accounts/101/balance"
$KEY = "atm-" + [DateTimeOffset]::Now.ToUnixTimeSeconds()
$body = Json '{"cuentaId":101,"monto":20000}'
curl.exe -sk -X POST "$GW/atm/withdrawals" -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" --data-binary $body
Write-Host "`n(mismo Idempotency-Key: no se debita dos veces)"
curl.exe -sk -X POST "$GW/atm/withdrawals" -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" --data-binary $body

Paso "5. Pagos: deposito de monto alto -> alerta de seguridad en Kafka"
$dep = Json '{"cuentaId":103,"monto":2500000,"descripcion":"Deposito grande"}'
curl.exe -sk -X POST "$GW/api/payments/deposits" -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" --data-binary $dep
Start-Sleep -Seconds 3
curl.exe -sk -H "Authorization: Bearer $ADM" "$GW/api/notifications?categoria=SEGURIDAD"

Paso "6. Spring Batch: jobs y resultados"
curl.exe -sk -H "Authorization: Bearer $ADM" "$GW/api/batch/jobs"
curl.exe -sk -H "Authorization: Bearer $ADM" "$GW/api/batch/reports/intereses?periodo=2024-12&cuentaId=101"

Paso "7. Fallo critico + reejecucion automatica"
curl.exe -sk -X POST -H "Authorization: Bearer $ADM" "$GW/api/batch/jobs/reporteTransaccionesDiariasJob/executions?fechaProceso=2024-12-31&simularFalloCriticoEnLinea=450&forzar=true"
Write-Host "`nEn ~20 s el job se reinicia solo. Ver: docker compose logs -f batch-service"
