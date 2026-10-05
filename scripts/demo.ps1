# Recorrido completo de pruebas (PowerShell). Uso: .\scripts\demo.ps1
$GW = "http://localhost:8080"
$AUTH = "http://localhost:9000/oauth2/token"

function Get-Token($clientId, $secret, $scope) {
    $basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${clientId}:${secret}"))
    (Invoke-RestMethod -Method Post -Uri $AUTH -Headers @{ Authorization = "Basic $basic" } `
        -Body @{ grant_type = "client_credentials"; scope = $scope }).access_token
}
function Call($method, $path, $token, $body) {
    $h = @{}; if ($token) { $h.Authorization = "Bearer $token" }
    try {
        if ($body) { Invoke-RestMethod -Method $method -Uri "$GW$path" -Headers $h -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 6 }
        else { Invoke-RestMethod -Method $method -Uri "$GW$path" -Headers $h | ConvertTo-Json -Depth 6 }
    } catch { "HTTP " + [int]$_.Exception.Response.StatusCode }
}
function Step($t) { Write-Host "`n== $t" -ForegroundColor Cyan }

$WEB = Get-Token "web-client" "web-secret" "web bank.read"
$MOB = Get-Token "mobile-client" "mobile-secret" "mobile bank.read"
$ADM = Get-Token "admin-client" "admin-secret" "web mobile bank.read bank.write"

Step "1. Sin token (esperado 401)";                      Call GET "/web/accounts/101/summary" $null
Step "2. Canal WEB (200, respuesta completa)";            Call GET "/web/accounts/101/summary" $WEB
Step "3. Canal MOVIL (200, respuesta liviana)";           Call GET "/mobile/accounts/101/summary" $MOB
Step "4. Token web en canal movil (esperado 403)";        Call GET "/mobile/accounts/101/summary" $WEB
Step "5. Token de lectura intenta escribir (403)";        Call POST "/api/transactions" $WEB '{"cuentaId":101,"tipo":"deposito","monto":1000}'
Step "6. Registrar transaccion con admin (201 + Kafka)";  Call POST "/api/transactions" $ADM '{"cuentaId":101,"tipo":"deposito","monto":25000,"descripcion":"Deposito de prueba"}'
Start-Sleep -Seconds 3
Step "7. Notificaciones del consumidor Kafka";            Call GET "/api/notifications" $ADM
Step "8. Cuenta inexistente (esperado 404)";              Call GET "/web/accounts/999/summary" $WEB
Write-Host "`nResiliencia: docker stop bank-accounts-service y repetir el paso 2 (degraded:true)"
