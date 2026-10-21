# ms-rutaexpress-rabbitmq - Microservicio de Mensajería Asíncrona (RabbitMQ)

Microservicio dedicado al procesamiento orientado a eventos (*Event-Driven Architecture*) mediante **RabbitMQ 3.13** y **Spring Boot AMQP**.

---

## 1. Rol en la Arquitectura Distribuida

Este componente corre en su **propia instancia AWS EC2 independiente** junto con el broker de mensajería RabbitMQ:

1. **Productor (`ms-rutaexpress-shipments`):** Cada vez que se crea un envío o cambia de estado en la máquina de estados, publica un evento JSON en el Topic Exchange `rutaexpress.events.exchange` con la Routing Key `shipment.event.<tipo>`.
2. **Broker (`rabbitmq-broker`):** Enruta y almacena de manera durable el mensaje en la cola `rutaexpress.audit.queue` (Puerto AMQP `5672`, Panel de Administración Web en puerto `15672`).
3. **Consumidor (`ms-rutaexpress-rabbitmq`):** Escucha la cola en tiempo real mediante `@RabbitListener`, registra el evento procesado y lo encamina hacia `ms-rutaexpress-audit` para su persistencia en **Amazon RDS (PostgreSQL)**.

---

## 2. Puertos y Endpoints

| Componente | Puerto | Descripción |
|---|---|---|
| Broker AMQP | `5672` | Comunicación de colas y exchanges entre microservicios |
| RabbitMQ Management UI | `15672` | Consola web de RabbitMQ (Usuario: `rutaexpress` / Pass: `rutaexpress123`) |
| API Microservicio | `8084` | API REST y Swagger UI (`/swagger-ui.html`) |

### Endpoints REST del Microservicio (`:8084`)
* `GET /api/rabbitmq/health` — Estado del consumidor, exchange, cola y contador de eventos procesados.
* `GET /api/rabbitmq/events` — Lista los últimos eventos consumidos desde la cola RabbitMQ.
* `POST /api/rabbitmq/publish` — Permite publicar un evento de prueba manualmente en el Exchange de RabbitMQ.

---

## 3. Despliegue Independiente en EC2

El despliegue se ejecuta automáticamente vía **GitHub Actions** ([`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml)) en la instancia `EC2_RABBITMQ_IP`, o manualmente con:

```bash
cd ms-rutaexpress-rabbitmq
docker compose up -d
```
