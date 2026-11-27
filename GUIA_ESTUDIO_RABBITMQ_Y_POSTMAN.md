# Guia de Estudio: RabbitMQ, Arquitectura de Mensajeria y Pruebas en Postman
## Caso 3 - RutaExpress (Evaluacion Parcial N°3 - DSY1107)

---

## 1. Ficha Tecnica de Infraestructura de Mensajeria

* **Servidor en la Nube (AWS EC2-Apps):** `3.81.237.202`
* **Cluster RabbitMQ:** 
  * Nodo 1: `rabbitmq-1` (Puerto AMQP: `5672` | Management Web: `15672`)
  * Nodo 2: `rabbitmq-2` (Puerto AMQP: `5673` | Management Web: `15673`)
* **Credenciales de Acceso:** 
  * Usuario: `rutaexpress`
  * Contraseña: `rutaexpress123`
* **Consola Web de RabbitMQ:** `http://3.81.237.202:15672`
* **Puerta de Entrada BFF (Backend-For-Frontend):** `http://3.81.237.202:8080`
* **Politica de Alta Disponibilidad:** `ha-all` aplicada sobre el patron de colas `^q\.`

---

## 2. Los Conceptos Fundamentales de RabbitMQ

### A. ¿Que es un Exchange?
Un **Exchange (Enrutador)** es el agente encargado de recibir los mensajes emitidos por los microservicios productores. En la arquitectura AMQP, un productor **nunca** deposita un mensaje directamente en una cola; se lo entrega a un Exchange junto con una etiqueta llamada **Routing Key**. El Exchange inspecciona la clave y decide a que colas copiar el mensaje.

#### Las 3 Variantes Implementadas en RutaExpress:
1. **`cmd.direct` (Direct Exchange):**
   * **Mecanismo:** Enrutamiento por coincidencia estricta y exacta.
   * **Funcionamiento:** Compara la Routing Key del mensaje con la Routing Key del Binding de la cola. Si son identicas, entrega el mensaje.
   * **Uso en el proyecto:** Comandos directos y unicos, como la orden de generar etiqueta (`label.gen`) o el ticket de bodega (`warehouse.ticket`).
2. **`cmd.topic` (Topic Exchange):**
   * **Mecanismo:** Enrutamiento por patrones jerarquicos con comodines separados por puntos (`.`):
     * `*` (asterisco): Sustituye exactamente una palabra (ejemplo: `label.*` captura `label.gen` y `label.print`, pero no `label.gen.pdf`).
     * `#` (numeral): Sustituye cero, una o multiples palabras (ejemplo: `warehouse.#` captura `warehouse.ticket`, `warehouse.stock.check`, etc.).
   * **Uso en el proyecto:** Difusion de eventos de dominio para que multiples areas operativas se suscriban sin acoplamiento.
3. **`cmd.dead.dlx` (Dead Letter Exchange - DLX):**
   * **Mecanismo:** Direct Exchange exclusivo para fallos.
   * **Funcionamiento:** No recibe mensajes normales del productor. Solo recibe mensajes expulsados de las colas principales cuando un consumidor los rechaza (`basicNack`) o cuando expira su tiempo de retencion (TTL).

---

### B. ¿Que es un Binding (Enlace)?
Un **Binding** es la regla de conexion que une a un Exchange con una Cola especifica:
* Sin Binding, el Exchange descarta el mensaje porque no sabe a quien enviarlo.
* El Binding define: *"Todo mensaje que llegue al Exchange [X] con la Routing Key [Y], copialo en la Cola [Z]"*.

---

### C. ¿Que es `label.gen` y `warehouse.ticket`?
Son **Routing Keys (Claves de Enrutamiento)** de negocio que identifican la intencion del evento:
* **`label.gen`:** Emitida cuando un envio pasa a estado `CREADO`. Solicita al subsistema de despacho emitir la guia y el codigo de barras.
* **`warehouse.ticket`:** Emitida cuando el envio pasa a estados operativos posteriores (`ACEPTADO`, `EN_BODEGA`, `EN_RUTA`). Solicita la preparacion fisica del paquete en bodega (picking).

---

### D. ¿Como se comportan las Colas en el Proyecto?
Una cola es una estructura **FIFO (First In, First Out)** persistente con las siguientes politicas:
1. **Durabilidad (`durable: true`):** Las colas y sus mensajes sobreviven a reinicios del servidor EC2 o de Docker.
2. **Tiempo de Vida (TTL - Time to Live):** 
   * Las colas principales tienen configurado `x-message-ttl = 30000` (30 segundos). Si un mensaje no es consumido en 30 segundos, expira y pasa a la DLQ.
   * Las colas DLQ tienen un TTL de 24 horas (`86400000 ms`) para dar tiempo al equipo de soporte de auditar el incidente.
3. **Desvío a DLQ (`x-dead-letter-exchange`):** Si el consumidor rechaza el mensaje con `channel.basicNack(requeue = false)`, RabbitMQ no reintenta infinitamente en bucle (evitando saturar la CPU); lo expulsa hacia `cmd.dead.dlx`.
4. **Espejado en Cluster (`ha-all`):** Todo mensaje que entra a `rabbitmq-1` se replica inmediatamente en `rabbitmq-2`.

---

## 3. Matriz de Componentes en Codigo Fuente

| Componente | Tipo | Archivo en Repositorio | Lineas Relevantes |
| :--- | :--- | :--- | :--- |
| `cmd.direct` | DirectExchange | `ms-rutaexpress-rabbitmq/.../RabbitMQConfig.java` | Lineas 78-80 |
| `cmd.topic` | TopicExchange | `ms-rutaexpress-rabbitmq/.../RabbitMQConfig.java` | Lineas 83-85 |
| `cmd.dead.dlx` | DirectExchange (DLX) | `ms-rutaexpress-rabbitmq/.../RabbitMQConfig.java` | Lineas 88-90 |
| `q.cmd.warehouse` | Queue con DLQ y TTL | `ms-rutaexpress-rabbitmq/.../RabbitMQConfig.java` | Lineas 97-103 |
| `q.cmd.warehouse.dlq` | Dead Letter Queue | `ms-rutaexpress-rabbitmq/.../RabbitMQConfig.java` | Lineas 106-110 |
| `q.cmd.label` | Queue con DLQ y TTL | `ms-rutaexpress-rabbitmq/.../RabbitMQConfig.java` | Lineas 132-138 |
| `q.cmd.label.dlq` | Dead Letter Queue | `ms-rutaexpress-rabbitmq/.../RabbitMQConfig.java` | Lineas 140-145 |
| `AuditClient` | Publicador RabbitTemplate | `ms-rutaexpress-shipments/.../AuditClient.java` | Lineas 83-100 |
| `WarehouseTicketListener` | Consumidor Bodega | `ms-rutaexpress-rabbitmq/.../WarehouseTicketListener.java` | Lineas 30-60 |
| `ShippingLabelListener` | Consumidor Etiquetas | `ms-rutaexpress-rabbitmq/.../ShippingLabelListener.java` | Lineas 30-55 |
| `DeadLetterQueueListener` | Consumidor DLQ Cuarentena | `ms-rutaexpress-rabbitmq/.../DeadLetterQueueListener.java` | Lineas 33-55 |
| `RabbitAdminService` | Administrador Dinamico | `ms-rutaexpress-rabbitmq/.../RabbitAdminService.java` | Lineas 33-120 |
| `BffRabbitMQController` | Fachada Gateway BFF | `ms-rutaexpress-bff/.../BffRabbitMQController.java` | Lineas 25-159 |

---

## 4. Como se Comunican y Complementan los 5 Microservicios

1. **`ms-rutaexpress-catalog` (Puerto 8082):**
   * *Rol:* Validador de Negocio y Stock.
   * *Complemento:* Cuando un cliente solicita un envio, `shipments` consulta a `catalog` para asegurar que el servicio existe y que hay cupos de vehiculos disponibles (`availableCapacity > 0`). Si no hay cupo, se bloquea la peticion y se evita sobrecargar a RabbitMQ con mensajes invalidos.

2. **`ms-rutaexpress-shipments` (Puerto 8081 - EL PRODUCTOR):**
   * *Rol:* Gestor Transaccional de Despachos.
   * *Complemento:* Registra el pedido en PostgreSQL (`rutaexpress_shipments`). Inmediatamente, mediante `AuditClient.java`, empaqueta el evento en un sobre JSON (`EventEnvelope`) con UUID unico y lo dispara a `cmd.direct` con la clave `label.gen` o `warehouse.ticket`. No se queda esperando a que la bodega responda (latencia menor a 25 ms).

3. **`ms-rutaexpress-rabbitmq` (Puerto 8084 - EL CONSUMIDOR Y WORKER):**
   * *Rol:* Procesador Operativo Asincrono.
   * *Complemento:* Sus Listeners (`WarehouseTicketListener` y `ShippingLabelListener`) reciben los mensajes desde las colas. Aplican confirmacion manual (`basicAck`). Si la impresora de etiquetas o el sistema de bodega falla, rechazan el mensaje con `basicNack(requeue=false)` para que se aisle en la DLQ sin tumbar a la API de clientes.

4. **`ms-rutaexpress-audit` (Puerto 8083):**
   * *Rol:* Bitacora Legal y Trazabilidad Inmutable.
   * *Complemento:* Mientras RabbitMQ gestiona el flujo de trabajo operativo inmediato (imprimir y embalar), `audit` persiste cada cambio de estado en PostgreSQL (`rutaexpress_audit`) para que el Auditor pueda consultar el historial completo en el frontend mediante `getFullTrace`.

5. **`ms-rutaexpress-bff` (Puerto 8080):**
   * *Rol:* Fachada Unificada y Guardián de Seguridad (RBAC).
   * *Complemento:* Centraliza el consumo desde el Frontend React. Valida los tokens de Azure Entra ID y exige rol `Admin` para acceder a la creacion de colas dinamicas, purga y metricas de RabbitMQ.

---

## 5. Guion de Defensa ante la Evaluacion

### Pregunta 1: ¿Por que tienen un microservicio separado llamado `ms-rutaexpress-rabbitmq` en lugar de consumir dentro de `shipments`?
> *"Profesor, aplicamos el patron **Worker Service / Event-Driven Consumer** para cumplir con el Principio de Responsabilidad Unica (SRP) y desacoplar los dominios. Si pusieramos la generacion de etiquetas, la preparacion de tickets de bodega y el manejo de Dead Letter Queues dentro de `shipments`, una falla en la impresora o una saturacion de bodega degradaria la API de cara al cliente. Separarlo permite que `shipments` responda en 20 ms mientras `rabbitmq-svc` procesa en segundo plano con confirmaciones manuales (ACK/NACK), y ademas nos permite centralizar la administracion dinamica con `RabbitAdmin` y las metricas de resiliencia."*

### Pregunta 2: ¿Que sucede si un mensaje tiene formato corrupto o falla su procesamiento?
> *"El consumidor captura la excepcion dentro de un bloque try-catch y ejecuta `channel.basicNack(deliveryTag, false, false)` con `requeue=false`. Al indicarle a RabbitMQ que no reencole el mensaje, el broker aplica la configuracion `x-dead-letter-exchange` de la cola y lo enruta automaticamente hacia el exchange `cmd.dead.dlx`, aterrizando en la cola de cuarentena correspondiente (`q.cmd.warehouse.dlq`). Esto evita loops infinitos de procesamiento y permite auditar el mensaje fallido sin perdida de datos."*

### Pregunta 3: ¿Como garantiza el clúster la Alta Disponibilidad (HA)?
> *"Levantamos dos nodos RabbitMQ (`rabbitmq-1` y `rabbitmq-2`) vinculados mediante una cookie secreta compartida de Erlang. Aplicamos la politica `ha-all` sobre el patron `^q\.`, lo que significa que cada mensaje recibido en el nodo primario se replica de manera sincronica en el nodo secundario. Si el nodo 1 sufre una caida, el nodo 2 toma el liderazgo de las colas sin interrupcion de servicio y los microservicios se reconectan automaticamente porque tienen configurada la propiedad `SPRING_RABBITMQ_ADDRESSES=rabbitmq-1:5672,rabbitmq-2:5672`."*

---

## 6. Coleccion de Pruebas Manuales para Postman

Configura en Postman una variable de entorno:
* `BASE_URL` = `http://3.81.237.202:8080`

---

### Prueba 1: Comprobar Salud del Microservicio RabbitMQ y Exchanges
Verifica que el servicio consumidor y los 3 exchanges declarativos esten en linea.

* **Metodo:** `GET`
* **URL:** `{{BASE_URL}}/api/bff/rabbitmq/health`
* **Headers:**
  * `Accept: application/json`
* **Codigo HTTP Esperado:** `200 OK`
* **Cuerpo de Respuesta Esperado:**
```json
{
  "status": "UP",
  "service": "ms-rutaexpress-rabbitmq",
  "exchanges": [
    "cmd.direct",
    "cmd.topic",
    "cmd.dead.dlx"
  ],
  "metrics": {
    "totalProcessed": 0,
    "totalInDlq": 0,
    "dlqRatePercent": 0.0,
    "uniqueEventIdsTracked": 0
  }
}
```

---

### Prueba 2: Publicacion y Procesamiento Exitoso de un Envio (Flujo Normal)
Registra un nuevo despacho, disparando la emision del evento a RabbitMQ hacia la cola `q.cmd.label`.

* **Metodo:** `POST`
* **URL:** `{{BASE_URL}}/api/bff/shipments`
* **Headers:**
  * `Content-Type: application/json`
* **Body (raw JSON):**
```json
{
  "serviceId": 1,
  "senderName": "Distribuidora Central",
  "senderAddress": "Av. Libertador 1020",
  "senderPhone": "+56911223344",
  "recipientName": "Comercializadora Norte",
  "recipientAddress": "Calle Los Alerces 450",
  "recipientEmail": "recepcion@norte.cl",
  "recipientPhone": "+56988776655",
  "weightKg": 3.5,
  "distanceKm": 18,
  "declaredValue": 45000,
  "specialInstructions": "Entregar en porteria de bodega"
}
```
* **Codigo HTTP Esperado:** `200 OK` o `201 Created`
* **Explicacion:** El envio se guarda en base de datos y publica un mensaje en `cmd.direct` con routing key `label.gen`. El consumidor `ShippingLabelListener` lo procesa y ejecuta `basicAck`.

---

### Prueba 3: Demostracion de Resiliencia y Desvio a Dead Letter Queue (DLQ)
Dispara intencionalmente un mensaje que fuerza un rechazo controlado (`basicNack`) en el consumidor.

* **Metodo:** `POST`
* **URL:** `{{BASE_URL}}/api/bff/shipments/demo/dlq`
* **Headers:**
  * `Content-Type: application/json`
* **Body (raw JSON):**
```json
{
  "trackingNumber": "RTX-PRUEBA-POSTMAN-DLQ",
  "details": "Simulacion de fallo en impresora de etiquetas para evaluacion Duoc"
}
```
* **Codigo HTTP Esperado:** `202 Accepted`
* **Cuerpo de Respuesta Esperado:**
```json
{
  "status": "SENT_TO_DLQ_FLOW",
  "targetDlq": "q.cmd.warehouse.dlq",
  "envelope": {
    "type": "DLQ_SIMULATION",
    "simulateFailure": true,
    "correlationId": "RTX-PRUEBA-POSTMAN-DLQ"
  }
}
```

---

### Prueba 4: Consultar Mensajes en Cuarentena en la DLQ
Comprueba que el mensaje anterior fue capturado por `DeadLetterQueueListener` y esta aislado para analisis.

* **Metodo:** `GET`
* **URL:** `{{BASE_URL}}/api/bff/rabbitmq/dlq`
* **Headers:**
  * `Accept: application/json`
* **Codigo HTTP Esperado:** `200 OK`
* **Cuerpo de Respuesta Esperado:**
```json
[
  {
    "dlqName": "q.cmd.warehouse.dlq",
    "reason": "Fallo en procesamiento de ticket de bodega",
    "type": "DLQ_SIMULATION",
    "payload": {
      "trackingNumber": "RTX-PRUEBA-POSTMAN-DLQ",
      "details": "Simulacion de fallo en impresora de etiquetas para evaluacion Duoc"
    }
  }
]
```

---

### Prueba 5: Consultar Metricas de Rendimiento y Tasa de Cuarentena
Valida el porcentaje de fallos desviados a DLQ frente a mensajes exitosos.

* **Metodo:** `GET`
* **URL:** `{{BASE_URL}}/api/bff/rabbitmq/metrics`
* **Headers:**
  * `Accept: application/json`
* **Codigo HTTP Esperado:** `200 OK`
* **Cuerpo de Respuesta Esperado:**
```json
{
  "totalProcessed": 1,
  "totalInDlq": 1,
  "dlqRatePercent": 50.0,
  "uniqueEventIdsTracked": 2
}
```

---

### Prueba 6: Creacion Dinamica de Cola con RabbitAdmin
Demuestra la capacidad de crear infraestructura programatica en tiempo real sin reiniciar contenedores.

* **Metodo:** `POST`
* **URL:** `{{BASE_URL}}/api/bff/rabbitmq/queues`
* **Headers:**
  * `Content-Type: application/json`
* **Body (raw JSON):**
```json
{
  "queueName": "q.cmd.postman.demo",
  "durable": true,
  "messageTtlMs": 60000,
  "deadLetterExchange": "cmd.dead.dlx",
  "deadLetterRoutingKey": "warehouse.dead"
}
```
* **Codigo HTTP Esperado:** `200 OK`
* **Cuerpo de Respuesta Esperado:**
```json
{
  "status": "CREATED",
  "queue": "q.cmd.postman.demo",
  "durable": true,
  "messageTtlMs": 60000,
  "deadLetterExchange": "cmd.dead.dlx"
}
```

---

### Prueba 7: Validacion de Entrada con @Valid (Prueba de Rechazo 400)
Comprueba que el backend valida los parametros y no permite crear colas con nombre vacio.

* **Metodo:** `POST`
* **URL:** `{{BASE_URL}}/api/bff/rabbitmq/queues`
* **Headers:**
  * `Content-Type: application/json`
* **Body (raw JSON):**
```json
{
  "queueName": "",
  "durable": true
}
```
* **Codigo HTTP Esperado:** `400 Bad Request`
* **Explicacion:** Valida la robustez del controlador ante cargas utiles invalidas.

---

### Prueba 8: Purga de una Cola con RabbitAdmin
Vacia todos los mensajes acumulados en una cola de forma remota.

* **Metodo:** `POST`
* **URL:** `{{BASE_URL}}/api/bff/rabbitmq/queues/q.cmd.postman.demo/purge`
* **Headers:**
  * `Accept: application/json`
* **Codigo HTTP Esperado:** `200 OK`
* **Cuerpo de Respuesta Esperado:**
```json
{
  "status": "PURGED",
  "queue": "q.cmd.postman.demo",
  "purgedMessages": 0
}
```
