# Kafka en RutaExpress: arquitectura, despliegue y pruebas

En RutaExpress, RabbitMQ se usa para **tareas** (etiqueta, ticket de bodega, email) y Kafka para el **streaming de eventos** (auditoría y reportería en tiempo real), como pide el Caso 3.

---

## 1. Arquitectura

```
                       ┌──────────────── EC2 KAFKA (infra/kafka) ────────────────┐
                       │ zookeeper-1/2/3   kafka-1:9092  kafka-2:9093  kafka-3:9094 │
                       │                   Kafka UI :8090                          │
                       └───────────────────────────────────────────────────────────┘
                                   ▲ produce                ▼ consume
 ms-rutaexpress-shipments ──► shipments.events (3P, RF3, delete, 7d) ──┬──► ms-rutaexpress-audit  (grupo audit-svc)
   key = trackingNumber                                                │      └─► audit.timeline (compact,delete, 30d)
   acks=all + idempotencia                                             │      └─► shipments.events.audit.DLT
                                                                       └──► ms-rutaexpress-report (grupo report-svc)
                                                                              └─► shipments.events.report.DLT
 audit.timeline ──► ms-rutaexpress-report (grupo report-timeline-svc): actividad por usuario / accion
                      └─► audit.timeline.report.DLT
 ms-rutaexpress-kafka (admin :8086): topics, lag, DLT/reproceso, simulaciones, prueba de carga
```

### Topics (sección 9 del caso)

| Topic | Particiones | Réplicas | Política | Retención | Propósito |
|---|---|---|---|---|---|
| `shipments.events` | 3 | 3 | delete | 7 días | Fuente de verdad de los eventos del envío |
| `audit.timeline` | 3 | 3 | compact,delete | 30 días | Quién / qué / cuándo / desde dónde |
| `shipments.events.audit.DLT` | 3 | 3 | delete | 14 días | Fallos definitivos del consumidor audit |
| `shipments.events.report.DLT` | 3 | 3 | delete | 14 días | Fallos definitivos del consumidor report |
| `audit.timeline.report.DLT` | 3 | 3 | delete | 14 días | Fallos definitivos del consumidor de `audit.timeline` en report |
| `rutaexpress.loadtest` | 3 | 3 | delete | 1 hora | Pruebas de carga / consumer lag |

Todos tienen `min.insync.replicas=2`: con `acks=all` el cluster tolera la caída de 1 broker sin perder escrituras.

### Cómo se aplica cada guía

| Guía | Dónde está en el código |
|---|---|
| 3.2.1 Topic, productor, consumidor, offsets y particiones | `ShipmentEventPublisher` (key = trackingNumber → orden por envío), listeners en audit/report con log de partition/offset |
| 3.3.1 Escalamiento, replicación, retención, lag | RF=3, min ISR=2, 3 particiones con `concurrency=3`, `GET /api/kafka/consumer-groups/{id}/lag` |
| 3.3.2 Tuning y pruebas de carga | `batch.size=32KB`, `linger.ms=20`, `compression=lz4`, `POST /api/kafka/lab/load-test` + consumidor lento |
| 3.3.3 Manejo de errores | `ErrorHandlingDeserializer`, `@Valid`, backoff exponencial (1s, 2s, 4s), `DeadLetterPublishingRecoverer`, idempotencia por `eventId`, `traceId` en MDC |

### Idempotencia

Cada cambio de estado genera **un solo `eventId`**, que viaja en RabbitMQ y también en Kafka. `ms-rutaexpress-audit` registra el evento una sola vez aunque le llegue dos veces (por Kafka y por el reenvío HTTP de `ms-rutaexpress-rabbitmq`). Para eso revisa `event_id` antes de guardar (patrón Check-then-Act) y, además, la columna tiene `UNIQUE`.

---

## 2. Probar en local (Docker)

El cluster completo (3 ZooKeeper + 3 brokers) necesita unos 4 GB solo para Kafka. En un PC con 8 GB de RAM se vuelve inestable: ZooKeeper pierde las sesiones y el controller cambia de broker una y otra vez. Para probar en el PC usa la versión liviana (1 ZooKeeper + 1 broker), que crea los mismos topics con 1 réplica:

```bash
# 1a. PC con poca RAM: Kafka liviano (recomendado en local)
docker compose -f infra/kafka/docker-compose.local.yml up -d

# 1b. PC con 16 GB o más: cluster completo, igual al de AWS
docker compose -f infra/kafka/docker-compose.yml up -d

docker logs kafka-init          # debe mostrar "OK -> shipments.events", etc.

# 2. Aplicaciones (postgres local con perfil "local")
mvn clean package -DskipTests
docker compose --profile local up -d --build
```

> Con la versión liviana, a `kafka-svc` agrégale `KAFKA_TOPIC_REPLICAS=1`.

- Kafka UI: http://localhost:8090
- Swagger de report: http://localhost:8085/swagger-ui.html
- Swagger del admin de Kafka: http://localhost:8086/swagger-ui.html

> Si tu Postgres local ya existía antes de este cambio, crea la base nueva:
> `docker exec rtx-postgres-db psql -U postgres -c "CREATE DATABASE rutaexpress_report;"`

---

## 3. Desplegar en AWS (EC2 de Kafka)

### 3.1 Crear la instancia
- **Tipo:** `t3.large` (8 GB) recomendado. Con `t3.medium` (4 GB) también funciona, pero agrega swap.
- **Misma VPC** que las otras instancias. Anota la **IP privada** (`172.31.x.x`).
- **Security Group de entrada:**

| Puerto | Origen | Uso |
|---|---|---|
| 22 | Tu IP | SSH |
| 9092-9094 | SG / IP privada de la EC2 de microservicios | Clientes Kafka |
| 8090 | Tu IP | Kafka UI |

### 3.2 Levantar el cluster
```bash
ssh -i rutaexpress-key.pem ec2-user@<IP_PUBLICA_EC2_KAFKA>
sudo yum install -y docker git && sudo systemctl enable --now docker
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

git clone -b develop https://github.com/<usuario>/rutaexpress-backend.git
cd rutaexpress-backend/infra/kafka
sudo KAFKA_HOST=<IP_PRIVADA_EC2_KAFKA> docker compose up -d
sudo docker logs -f kafka-init
```

### 3.3 Conectar los microservicios
1. En GitHub → *Settings → Secrets → Actions* crea `KAFKA_BOOTSTRAP_SERVERS`:
   `172.31.x.x:9092,172.31.x.x:9093,172.31.x.x:9094` (IP privada de la EC2 de Kafka).
2. En la EC2 de base de datos crea la base nueva:
   `sudo docker exec rtx-postgres-db psql -U postgres -c "CREATE DATABASE rutaexpress_report;"`
3. Haz push a `develop`: el pipeline compila y publica `ms-rutaexpress-report` y `ms-rutaexpress-kafka`, y las despliega.
4. Actualiza el stack de API Gateway con `infra/aws/apigateway-rutaexpress.yml` (incluye las rutas `/api/report/*` y `/api/kafka/*`).

---

## 4. Guion de demostración (vía BFF :8080, rol Admin)

```bash
BFF=http://localhost:8080

# Estado del cluster: 3 brokers, controller
curl $BFF/api/bff/kafka/cluster
# Topología: líder, réplicas, ISR, retención por partición
curl $BFF/api/bff/kafka/topics/shipments.events

# Flujo real: crear envío y cambiar estado -> evento en shipments.events
curl -X POST $BFF/api/bff/shipments -H 'Content-Type: application/json' -d '{"serviceId":1,"senderName":"Ana","senderAddress":"Calle 1","recipientName":"Luis","recipientAddress":"Calle 2","weightKg":2.5,"distanceKm":10}'
curl -X PUT $BFF/api/bff/shipments/1/status -H 'Content-Type: application/json' -d '{"status":"ACEPTADO","performedBy":"despachador@rutaexpress.cl"}'

# Auditoría (consumida desde Kafka) y KPIs
curl $BFF/api/bff/audit/shipments/1
curl "$BFF/api/bff/report/kpis?range=last24h"
curl "$BFF/api/bff/report/top-services?range=last7d"
# Actividad por usuario (consumida desde el segundo topic: audit.timeline)
curl "$BFF/api/bff/report/activity?range=last24h"

# Administración de topics y consumer groups
curl -X POST $BFF/api/bff/kafka/topics -H 'Content-Type: application/json' -d '{"name":"rutaexpress.demo","partitions":3,"replicationFactor":3,"configs":{"retention.ms":"3600000"}}'
curl -X PUT $BFF/api/bff/kafka/topics/rutaexpress.demo/configs -H 'Content-Type: application/json' -d '{"set":{"retention.ms":"86400000","cleanup.policy":"delete"}}'
curl -X POST $BFF/api/bff/kafka/topics/rutaexpress.demo/partitions -H 'Content-Type: application/json' -d '{"totalCount":6}'
curl -X DELETE $BFF/api/bff/kafka/topics/rutaexpress.demo
curl $BFF/api/bff/kafka/consumer-groups                           # audit-svc, report-svc, report-timeline-svc

# Key -> misma partición (orden garantizado)
for i in 1 2 3; do curl -X POST "$BFF/api/bff/kafka/lab/publish?key=RTX-ORDEN-555"; echo; done

# Manejo de errores
curl -X POST $BFF/api/bff/kafka/lab/simulate/transient-failure   # 3 reintentos (1s,2s,4s) -> DLT
curl -X POST $BFF/api/bff/kafka/lab/simulate/poison-pill         # JSON roto -> DLT inmediato
curl -X POST $BFF/api/bff/kafka/lab/simulate/invalid-payload     # falla @Valid -> DLT inmediato
curl $BFF/api/bff/kafka/dlt/shipments.events.audit.DLT/messages  # headers con la excepción
curl -X POST "$BFF/api/bff/kafka/dlt/shipments.events.audit.DLT/reprocess?partition=0&offset=0"

# Consumer lag (prueba de carga)
curl -X POST "$BFF/api/bff/kafka/lab/load-test?count=50000"
curl $BFF/api/bff/kafka/consumer-groups/loadtest-group/lag       # lag alto que baja
curl -X POST $BFF/api/bff/kafka/lab/load-test/consumer/pause     # el lag deja de bajar
curl -X POST $BFF/api/bff/kafka/lab/load-test/consumer/resume
```

### Tolerancia a fallos (en la EC2 de Kafka)
```bash
sudo docker stop kafka-2
sudo docker exec kafka-1 kafka-topics --bootstrap-server kafka-1:29092 --describe --topic shipments.events
#  -> el líder se reasigna, ISR queda en 2 réplicas, y los envíos se siguen publicando (min ISR = 2)
sudo docker start kafka-2
```

### Comandos útiles
```bash
sudo docker exec kafka-1 kafka-consumer-groups --bootstrap-server kafka-1:29092 --describe --group audit-svc
sudo docker exec kafka-1 kafka-console-consumer --bootstrap-server kafka-1:29092 --topic shipments.events --from-beginning --property print.key=true --property print.partition=true --property print.offset=true
```
