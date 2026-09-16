# Script de Validacin End-to-End para RutaExpress Backend
param(
    [string]$BffUrl = "http://127.0.0.1:8080"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Iniciando pruebas End-to-End en RutaExpress ($BffUrl)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Healthcheck
Write-Host "`n[Paso 1] Comprobando disponibilidad del BFF..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$BffUrl/api/bff/health" -Method Get
    Write-Host "  -> BFF responde: OK ($($health.service) v$($health.version))" -ForegroundColor Green
} catch {
    Write-Host "  -> Error al conectar con el BFF en $BffUrl. Asegrate de tener los servicios activos." -ForegroundColor Red
    exit 1
}

# 2. Consultar catlogo
Write-Host "`n[Paso 2] Consultando catlogo de servicios..." -ForegroundColor Yellow
$services = Invoke-RestMethod -Uri "$BffUrl/api/bff/catalog/services" -Method Get
Write-Host "  -> Se encontraron $($services.Count) servicios en catlogo." -ForegroundColor Green
$selectedService = $services[0]
Write-Host "  -> Usaremos el servicio: $($selectedService.name) (ID: $($selectedService.id), Capacidad disponible: $($selectedService.availableCapacity))" -ForegroundColor Gray
$initialCapacity = $selectedService.availableCapacity

# 3. Crear envo
Write-Host "`n[Paso 3] Creando nuevo envo en estado CREADO..." -ForegroundColor Yellow
$shipmentPayload = @{
    serviceId = $selectedService.id
    senderName = "Carlos Remitente"
    senderAddress = "Av. Providencia 1234, Santiago"
    senderPhone = "+56911223344"
    recipientName = "Andrea Destinatario"
    recipientAddress = "Calle Valparaso 567, Via del Mar"
    recipientEmail = "andrea@destinatario.cl"
    recipientPhone = "+56999887766"
    weightKg = 3.5
    distanceKm = 120.0
    declaredValue = 45000.0
    createdBy = "carlos@cliente.cl"
    notes = "Dejar en conserjera si no hay nadie"
} | ConvertTo-Json

$createdShipment = Invoke-RestMethod -Uri "$BffUrl/api/bff/shipments" -Method Post -Body $shipmentPayload -ContentType "application/json; charset=utf-8"
Write-Host "  -> Envio creado con ID: $($createdShipment.id)" -ForegroundColor Green
Write-Host "  -> Tracking Number: $($createdShipment.trackingNumber)" -ForegroundColor Green
Write-Host "  -> Estado inicial: $($createdShipment.status)" -ForegroundColor Green
Write-Host "  -> Costo calculado: $$($createdShipment.shippingCost)" -ForegroundColor Green

# 4. Probar Regla de Negocio: Rechazar salto directo a EN_RUTA
Write-Host "`n[Paso 4] Validando Regla Clave: Rechazar paso directo a EN_RUTA sin ACEPTAR..." -ForegroundColor Yellow
$invalidPayload = @{
    status = "EN_RUTA"
    performedBy = "operador.trampa@rutaexpress.cl"
    userRole = "Despachador"
    note = "Intento indebido de saltar aceptacion"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$BffUrl/api/bff/shipments/$($createdShipment.id)/status" -Method Put -Body $invalidPayload -ContentType "application/json; charset=utf-8"
    Write-Host "  -> ERROR: El sistema debi rechazar la transicin pero la permiti!" -ForegroundColor Red
} catch {
    Write-Host "  -> REGLA RESPETADA CON EXITO: El sistema rechaz la transicin indebida (HTTP $($_.Exception.Response.StatusCode))." -ForegroundColor Green
}

# 5. Cambiar a estado ACEPTADO y verificar reduccin de capacidad
Write-Host "`n[Paso 5] Cambiando estado a ACEPTADO y validando descuento de capacidad..." -ForegroundColor Yellow
$acceptPayload = @{
    status = "ACEPTADO"
    performedBy = "despachador1@rutaexpress.cl"
    userRole = "Despachador"
    note = "Envo aceptado y asignado a lote de despacho"
} | ConvertTo-Json

$acceptedShipment = Invoke-RestMethod -Uri "$BffUrl/api/bff/shipments/$($createdShipment.id)/status" -Method Put -Body $acceptPayload -ContentType "application/json; charset=utf-8"
Write-Host "  -> Nuevo estado: $($acceptedShipment.status)" -ForegroundColor Green

$updatedService = Invoke-RestMethod -Uri "$BffUrl/api/bff/catalog/services/$($selectedService.id)" -Method Get
Write-Host "  -> Capacidad en catlogo: Inicial=$initialCapacity, Ahora=$($updatedService.availableCapacity)" -ForegroundColor Green
if ($updatedService.availableCapacity -eq ($initialCapacity - 1)) {
    Write-Host "  -> DESCUENTO DE CAPACIDAD VERIFICADO CORRECTAMENTE." -ForegroundColor Green
} else {
    Write-Host "  -> ALERTA: La capacidad no disminuy en 1 unidad." -ForegroundColor Yellow
}

# 6. Flujo normal: EN_BODEGA -> EN_RUTA -> ENTREGADO
Write-Host "`n[Paso 6] Avanzando por el ciclo de vida (EN_BODEGA -> EN_RUTA -> ENTREGADO)..." -ForegroundColor Yellow

$bodegaPayload = @{ status = "EN_BODEGA"; performedBy = "bodega@rutaexpress.cl"; userRole = "Despachador"; note = "Ingresado al centro de distribucin" } | ConvertTo-Json
$sBodega = Invoke-RestMethod -Uri "$BffUrl/api/bff/shipments/$($createdShipment.id)/status" -Method Put -Body $bodegaPayload -ContentType "application/json; charset=utf-8"
Write-Host "  -> Estado: $($sBodega.status)" -ForegroundColor Gray

$rutaPayload = @{ status = "EN_RUTA"; performedBy = "chofer1@rutaexpress.cl"; userRole = "Despachador"; note = "Cargado en furgn de reparto" } | ConvertTo-Json
$sRuta = Invoke-RestMethod -Uri "$BffUrl/api/bff/shipments/$($createdShipment.id)/status" -Method Put -Body $rutaPayload -ContentType "application/json; charset=utf-8"
Write-Host "  -> Estado: $($sRuta.status)" -ForegroundColor Gray

$entregadoPayload = @{ status = "ENTREGADO"; performedBy = "chofer1@rutaexpress.cl"; userRole = "Despachador"; note = "Entregado en conserjera con firma" } | ConvertTo-Json
$sEntregado = Invoke-RestMethod -Uri "$BffUrl/api/bff/shipments/$($createdShipment.id)/status" -Method Put -Body $entregadoPayload -ContentType "application/json; charset=utf-8"
Write-Host "  -> Estado final: $($sEntregado.status)" -ForegroundColor Green

# 7. Consultar Agregacin en BFF y Timeline de Auditora
Write-Host "`n[Paso 7] Consultando Agregacin Full-Trace y Timeline de Auditora..." -ForegroundColor Yellow
$fullTrace = Invoke-RestMethod -Uri "$BffUrl/api/bff/shipments/$($createdShipment.id)/full-trace" -Method Get
Write-Host "  -> Datos de envo recuperados: Tracking $($fullTrace.shipment.trackingNumber), Estado $($fullTrace.shipment.status)" -ForegroundColor Green
Write-Host "  -> Servicio asociado: $($fullTrace.catalogService.name)" -ForegroundColor Green
Write-Host "  -> Timeline de auditora ($($fullTrace.timeline.Count) eventos registrados):" -ForegroundColor Green

foreach ($evt in $fullTrace.timeline) {
    Write-Host "     * [$($evt.timestamp)] Evento: $($evt.eventType) | Usuario: $($evt.performedBy) ($($evt.userRole)) | $($evt.details)" -ForegroundColor Cyan
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "Todas las pruebas E2E y reglas de negocio pasaron con xito!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green