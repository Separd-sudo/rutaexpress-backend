#!/bin/bash
# Topologia Kafka del Caso RutaExpress (seccion 9)
#  - shipments.events : 3 particiones, RF 3, delete, 7 dias
#  - audit.timeline   : 3 particiones, RF 3, compact,delete, 30 dias
#  - *.DLT            : 3 particiones, RF 3, delete, 14 dias (uno por consumidor)
#  - rutaexpress.loadtest : topic de pruebas de carga / consumer lag (retencion corta)
set -e
# En AWS: 3 brokers, RF 3, min ISR 2. En local liviano: BS=kafka-1:29092 REPLICAS=1 MIN_ISR=1
BS="${BS:-kafka-1:29092,kafka-2:29092,kafka-3:29092}"
REPLICAS="${REPLICAS:-3}"
MIN_ISR="${MIN_ISR:-2}"

DAY=86400000

create() {
  local topic=$1 cleanup=$2 retention=$3
  kafka-topics --bootstrap-server "$BS" --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor "$REPLICAS" \
    --config cleanup.policy="$cleanup" \
    --config retention.ms="$retention" \
    --config min.insync.replicas="$MIN_ISR"
  echo "OK -> $topic ($cleanup, retention.ms=$retention)"
}

create shipments.events            delete          $((7 * DAY))
create audit.timeline              compact,delete  $((30 * DAY))
create shipments.events.audit.DLT  delete          $((14 * DAY))
create shipments.events.report.DLT delete          $((14 * DAY))
create rutaexpress.loadtest        delete          $((60 * 60 * 1000))

echo "=== Topics actuales ==="
kafka-topics --bootstrap-server "$BS" --describe
