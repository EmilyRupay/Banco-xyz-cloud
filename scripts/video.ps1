# =====================================================================================
# Comandos para GRABAR EL VIDEO (Windows PowerShell).
# Cargar UNA vez en la ventana de comandos:      . .\scripts\video.ps1
# (ojo: es punto, espacio, punto-barra)          Luego escribir: tokens, paso1, paso2, ...
# =====================================================================================
$global:GW = "https://localhost:8443"

function Linea($t) { Write-Host "`n>>> $t" -ForegroundColor Cyan }
function Json($texto) { $f = New-TemporaryFile; Set-Content -Path $f -Value $texto -NoNewline; return "@$f" }
function Bonito { param([Parameter(ValueFromPipeline = $true)] $txt) process { try { $txt | ConvertFrom-Json | ConvertTo-Json -Depth 6 } catch { $txt } } }
function Tk($c, $s) { (curl.exe -sk -u $c -d grant_type=client_credentials -d "scope=$s" "$global:GW/oauth2/token" | ConvertFrom-Json).access_token }

# ---- Antes de grabar (y otra vez si pasan mas de 5 minutos) ----
function tokens {
    $global:WEB = Tk "web-client:web-secret" "web bank.read bank.write"
    $global:MOB = Tk "mobile-client:mobile-secret" "mobile bank.read"
    $global:ATM = Tk "atm-client:atm-secret" "atm bank.read bank.write"
    $global:ADM = Tk "admin-client:admin-secret" "web mobile atm bank.read bank.write batch.admin"
    if ($global:WEB) { Write-Host "Tokens listos (web, mobile, atm, admin)" -ForegroundColor Green }
    else { Write-Host "ERROR: no se obtuvieron tokens. Revisar el gateway." -ForegroundColor Red }
}

# ---- PARTE 1: Spring Batch ----
function paso1 {
    Linea "Resultado de los 3 jobs ejecutados al iniciar"
    docker compose logs batch-service | Select-String "<<< Fin" | ForEach-Object { ($_ -split "ResumenEjecucionListener\s+:\s+")[-1] }
    Linea "Intereses mensuales: cuentas por particion (4 hilos en paralelo)"
    docker compose logs batch-service | Select-String "calcularInteresesWorkerStep:particion" | Select-Object -First 4 | ForEach-Object { ($_ -split ":\s+step=")[-1] }
    Linea "Intereses mensuales: registros descartados por motivo"
    curl.exe -sk -H "Authorization: Bearer $global:ADM" "$global:GW/api/batch/reports/rechazos?job=INTERESES&clave=2024-12" | Bonito
}

function paso2 {
    Linea "Simulo un FALLO CRITICO en la linea 450 del reporte diario (mirar la ventana de logs)"
    curl.exe -sk -o NUL -w "Job lanzado: HTTP %{http_code}`n" -X POST -H "Authorization: Bearer $global:ADM" `
        "$global:GW/api/batch/jobs/reporteTransaccionesDiariasJob/executions?fechaProceso=2024-12-31&simularFalloCriticoEnLinea=450&forzar=true"
}

# ---- PARTE 2: BFF ----
function paso3 {
    Linea "Misma cuenta 101: tamano de la respuesta WEB vs MOVIL"
    curl.exe -sk -o NUL -w "BFF web:   %{size_download} bytes`n" -H "Authorization: Bearer $global:WEB" "$global:GW/web/accounts/101/summary"
    curl.exe -sk -o NUL -w "BFF movil: %{size_download} bytes`n" -H "Authorization: Bearer $global:MOB" "$global:GW/mobile/accounts/101/summary"
    Linea "Respuesta del BFF movil (solo lo esencial)"
    curl.exe -sk -H "Authorization: Bearer $global:MOB" "$global:GW/mobile/accounts/101/summary" | Bonito
    Linea "Token del canal WEB usado en el canal MOVIL"
    curl.exe -sk -o NUL -w "HTTP %{http_code} (prohibido)`n" -H "Authorization: Bearer $global:WEB" "$global:GW/mobile/accounts/101/summary"
}

function paso4 {
    $global:KEY = "atm-" + (Get-Random)
    Linea "Cajero ATM-001: saldo ANTES del retiro"
    curl.exe -sk -H "Authorization: Bearer $global:ATM" -H "X-ATM-ID: ATM-001" "$global:GW/atm/accounts/101/balance" | Bonito
    Linea "Retiro de 20.000 con Idempotency-Key $global:KEY"
    curl.exe -sk -X POST "$global:GW/atm/withdrawals" -H "Authorization: Bearer $global:ATM" -H "X-ATM-ID: ATM-001" `
        -H "Idempotency-Key: $global:KEY" -H "Content-Type: application/json" --data-binary (Json '{"cuentaId":101,"monto":20000}') | Bonito
}

function paso5 {
    Linea "El cajero REINTENTA el mismo retiro (misma llave $global:KEY)"
    curl.exe -sk -X POST "$global:GW/atm/withdrawals" -H "Authorization: Bearer $global:ATM" -H "X-ATM-ID: ATM-001" `
        -H "Idempotency-Key: $global:KEY" -H "Content-Type: application/json" --data-binary (Json '{"cuentaId":101,"monto":20000}') | Bonito
    Linea "Saldo DESPUES: solo se desconto una vez"
    curl.exe -sk -H "Authorization: Bearer $global:ATM" -H "X-ATM-ID: ATM-001" "$global:GW/atm/accounts/101/balance" | Bonito
    Linea "Terminal NO registrado"
    curl.exe -sk -o NUL -w "HTTP %{http_code} (prohibido)`n" -H "Authorization: Bearer $global:ATM" -H "X-ATM-ID: ATM-999" "$global:GW/atm/accounts/101/balance"
}

# ---- PARTE 3: microservicios, Kafka y resiliencia ----
function paso6 {
    Linea "Deposito de 2.500.000 en la cuenta 103 (mirar la ventana de logs: evento + ALERTA)"
    curl.exe -sk -X POST "$global:GW/api/payments/deposits" -H "Authorization: Bearer $global:ADM" `
        -H "Content-Type: application/json" --data-binary (Json '{"cuentaId":103,"monto":2500000,"descripcion":"Deposito grande"}') | Bonito
}

function paso7 {
    Linea "Detengo el servicio de CUENTAS"
    docker compose stop accounts-service
    Linea "BFF web sigue respondiendo, con datos degradados"
    $r = curl.exe -sk -H "Authorization: Bearer $global:WEB" "$global:GW/web/accounts/101/summary" | ConvertFrom-Json
    [pscustomobject]@{ degraded = $r.degraded; advertencias = ($r.advertencias -join " | ") } | Format-List
    Linea "Cajero: no entrega datos sin confirmar"
    curl.exe -sk -w "  (HTTP %{http_code})`n" -H "Authorization: Bearer $global:ATM" -H "X-ATM-ID: ATM-001" "$global:GW/atm/accounts/101/balance"
}

function paso8 {
    Linea "Vuelvo a levantar el servicio de cuentas"
    docker compose start accounts-service
}

Write-Host "Comandos cargados. Primero escribe: tokens   y luego paso1 ... paso8" -ForegroundColor Green
