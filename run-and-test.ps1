function WaitFor-Http($url, $name, $maxWaitSec = 45) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $maxWaitSec) {
        try {
            $resp = Invoke-WebRequest -Uri $url -Method Get -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) {
                Write-Host "  -> [$name] listo y respondiendo HTTP 200 en $($sw.Elapsed.TotalSeconds.ToString("0.0"))s" -ForegroundColor Green
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 600
        }
    }
    Write-Host "  -> TIMEOUT esperando $name ($url)" -ForegroundColor Red
    return $false
}

Write-Host "Limpiando procesos previos..." -ForegroundColor Yellow
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

$cwd = (Get-Location).Path
Write-Host "Iniciando microservicios secuencialmente..." -ForegroundColor Cyan

# 1. Catalog
Write-Host "Iniciando Catalog..."
$pCatalog = Start-Process -FilePath "java" -ArgumentList "-Djava.net.preferIPv4Stack=true", "-jar", "ms-rutaexpress-catalog\target\ms-rutaexpress-catalog-1.0.0.jar" -WorkingDirectory $cwd -RedirectStandardOutput "logs_catalog.log" -RedirectStandardError "logs_catalog_err.log" -PassThru
$readyCatalog = WaitFor-Http "http://127.0.0.1:8082/api/catalog/services" "Catalog"

# 2. Audit
Write-Host "Iniciando Audit..."
$pAudit = Start-Process -FilePath "java" -ArgumentList "-Djava.net.preferIPv4Stack=true", "-jar", "ms-rutaexpress-audit\target\ms-rutaexpress-audit-1.0.0.jar" -WorkingDirectory $cwd -RedirectStandardOutput "logs_audit.log" -RedirectStandardError "logs_audit_err.log" -PassThru
$readyAudit = WaitFor-Http "http://127.0.0.1:8083/api/audit" "Audit"

# 3. Shipments
Write-Host "Iniciando Shipments..."
$pShipments = Start-Process -FilePath "java" -ArgumentList "-Djava.net.preferIPv4Stack=true", "-jar", "ms-rutaexpress-shipments\target\ms-rutaexpress-shipments-1.0.0.jar" -WorkingDirectory $cwd -RedirectStandardOutput "logs_shipments.log" -RedirectStandardError "logs_shipments_err.log" -PassThru
$readyShipments = WaitFor-Http "http://127.0.0.1:8081/actuator/health" "Shipments"

# 4. BFF
Write-Host "Iniciando BFF..."
$pBff = Start-Process -FilePath "java" -ArgumentList "-Djava.net.preferIPv4Stack=true", "-jar", "ms-rutaexpress-bff\target\ms-rutaexpress-bff-1.0.0.jar" -WorkingDirectory $cwd -RedirectStandardOutput "logs_bff.log" -RedirectStandardError "logs_bff_err.log" -PassThru
$readyBff = WaitFor-Http "http://127.0.0.1:8080/api/bff/health" "BFF"

if ($readyCatalog -and $readyAudit -and $readyShipments -and $readyBff) {
    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host "Todos los 4 microservicios estan ONLINE y respondiendo OK." -ForegroundColor Green
    Write-Host "Ejecutando suite de pruebas E2E..." -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    powershell -ExecutionPolicy Bypass -File .\test-e2e.ps1
} else {
    Write-Host "`nError: Algunos servicios no lograron iniciar a tiempo." -ForegroundColor Red
}

Write-Host "`nDeteniendo procesos de prueba..." -ForegroundColor Yellow
Stop-Process -Id $pCatalog.Id, $pAudit.Id, $pShipments.Id, $pBff.Id -Force -ErrorAction SilentlyContinue
Write-Host "Pruebas finalizadas." -ForegroundColor Green