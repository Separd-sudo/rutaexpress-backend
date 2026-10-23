#!/usr/bin/env zsh
# ==============================================================================
# RutaExpress - Script de Pruebas End-to-End (macOS / Linux) + RabbitMQ & DLQ
# ==============================================================================
set -e

BFF_URL="${BFF_URL:-http://localhost:8080}"
RABBIT_URL="${RABBIT_URL:-http://localhost:8084}"

echo "=================================================================="
echo " INICIANDO PRUEBAS E2E RUTAEXPRESS (BFF + RABBITMQ + DLQ)"
echo " BFF URL:    $BFF_URL"
echo " RABBIT URL: $RABBIT_URL"
echo "=================================================================="

echo "\n[1/6] Verificando salud del BFF..."
curl -sSf "$BFF_URL/api/bff/health" | grep -q "UP" && echo "  -> OK: BFF en estado UP"

echo "\n[2/6] Verificando salud del Microservicio RabbitMQ..."
curl -sSf "$RABBIT_URL/api/rabbitmq/health" | grep -q "UP" && echo "  -> OK: ms-rutaexpress-rabbitmq en estado UP"

echo "\n[3/6] Probando RabbitAdminService: Crear Cola, Exchange y Binding dinámicos..."
curl -sSf -X POST "$RABBIT_URL/api/rabbitmq/exchanges" \
  -H "Content-Type: application/json" \
  -d '{"exchangeName":"cmd.direct.e2e","type":"direct","durable":true}' && echo "\n  -> OK: Exchange creado"

curl -sSf -X POST "$RABBIT_URL/api/rabbitmq/queues" \
  -H "Content-Type: application/json" \
  -d '{"queueName":"q.cmd.e2e","durable":true,"messageTtlMs":30000,"deadLetterExchange":"cmd.dead.dlx","deadLetterRoutingKey":"warehouse.dead"}' && echo "\n  -> OK: Cola creada"

curl -sSf -X POST "$RABBIT_URL/api/rabbitmq/bindings" \
  -H "Content-Type: application/json" \
  -d '{"queueName":"q.cmd.e2e","exchangeName":"cmd.direct.e2e","routingKey":"e2e.test"}' && echo "\n  -> OK: Binding creado"

echo "\n[4/6] Probando Validación de Parámetros (@Valid): Rechazo de cola con nombre vacío..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RABBIT_URL/api/rabbitmq/queues" \
  -H "Content-Type: application/json" \
  -d '{"queueName":""}')
if [ "$HTTP_CODE" = "400" ]; then
  echo "  -> OK: Validación funcionó correctamente (HTTP 400 Bad Request)"
else
  echo "  -> ERROR: Se esperaba HTTP 400 y se recibió $HTTP_CODE"
  exit 1
fi

echo "\n[5/6] Probando flujo de fallo controlado hacia Dead Letter Queue (DLQ)..."
curl -sSf -X POST "$BFF_URL/api/shipments/demo/dlq" \
  -H "Content-Type: application/json" \
  -d '{"trackingNumber":"RTX-DLQ-TEST-001","details":"Prueba E2E de rechazo basicNack y envio a DLQ"}' && echo "\n  -> OK: Evento de prueba enviado al flujo DLQ"

sleep 2
echo "\n[6/6] Consultando métricas de procesamiento y mensajes en cuarentena (DLQ)..."
curl -sSf "$RABBIT_URL/api/rabbitmq/metrics" && echo ""
curl -sSf "$RABBIT_URL/api/rabbitmq/dlq" && echo ""

echo "\nLimpiando cola y exchange de prueba E2E..."
curl -sSf -X DELETE "$RABBIT_URL/api/rabbitmq/queues/q.cmd.e2e" > /dev/null
curl -sSf -X DELETE "$RABBIT_URL/api/rabbitmq/exchanges/cmd.direct.e2e" > /dev/null

echo "\n=================================================================="
echo " TODAS LAS PRUEBAS E2E FINALIZARON CON ÉXITO"
echo "=================================================================="
