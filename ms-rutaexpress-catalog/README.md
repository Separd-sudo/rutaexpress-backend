# ms-rutaexpress-catalog

Microservicio de dominio encargado de la gestion centralizada de los tipos de servicios de envio, parametros tarifarios y capacidad operacional disponible de la flota de vehiculos en RutaExpress.

---

## 1. Responsabilidades del Dominio

- Mantenimiento del catalogo de tipos de despacho (ej. Mismo Dia Express, Estandar Dia Siguiente, Economico).
- Parametrizacion de precios base y costo variable por kilometro para el calculo de tarifas.
- Control transaccional de cupos de despacho diario (`maxDailyCapacity` y `availableCapacity`).
- Exposicion de operaciones atomicas para reservar cupos de flota cuando un envio entra en estado aceptado y liberarlos en cancelaciones.

---

## 2. Endpoints del Servicio

| Metodo | Ruta | Descripcion | Codigo Exito |
|---|---|---|---|
| `GET` | `/api/catalog/services` | Lista todos los servicios de catalogo (parametro opcional `activeOnly=true`) | 200 OK |
| `GET` | `/api/catalog/services/{id}` | Obtiene informacion detallada de un servicio por ID | 200 OK |
| `GET` | `/api/catalog/services/code/{code}` | Consulta servicio por codigo unico (ej. `SAME_DAY`) | 200 OK |
| `POST` | `/api/catalog/services` | Registra un nuevo servicio con su tarifa y capacidad inicial | 201 Created |
| `PUT` | `/api/catalog/services/{id}` | Actualiza tarifas, capacidad maxima o disponibilidad | 200 OK |
| `POST` | `/api/catalog/services/{id}/reserve-capacity` | Reduce la capacidad disponible en la cantidad solicitada (parametro `amount`) | 200 OK |
| `POST` | `/api/catalog/services/{id}/release-capacity` | Incrementa la capacidad disponible sin superar el limite diario | 200 OK |

---

## 3. Ejemplos de Intercambio de Datos

### Peticion: Creacion de Servicio (`POST /api/catalog/services`)
```json
{
  "code": "URGENT_EVENING",
  "name": "Entrega Nocturna Urgente",
  "description": "Entrega en el mismo dia entre 19:00 y 23:00 hrs",
  "basePrice": 5990.00,
  "pricePerKm": 450.00,
  "maxDailyCapacity": 15,
  "active": true
}
```

### Peticion: Reserva de Capacidad (`POST /api/catalog/services/1/reserve-capacity?amount=1`)
Respuesta exitosa:
```json
{
  "serviceId": 1,
  "serviceCode": "SAME_DAY",
  "previousAvailableCapacity": 25,
  "newAvailableCapacity": 24,
  "maxDailyCapacity": 25,
  "message": "Capacidad reservada exitosamente"
}
```

Si la capacidad disponible es menor al cupo solicitado, retorna HTTP 409 Conflict:
```json
{
  "timestamp": "2026-09-16T19:30:00",
  "status": 409,
  "error": "Capacidad insuficiente",
  "message": "Capacidad de flota insuficiente para el servicio Express Mismo Dia. Disponible: 0, Solicitada: 1"
}
```

---

## 4. Variables de Entorno

| Variable | Valor por Defecto | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8082` | Puerto de escucha del microservicio |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:catalogdb` | URL de la base de datos (H2 en memoria o PostgreSQL) |
| `SPRING_DATASOURCE_USERNAME` | `sa` | Usuario de base de datos |
| `SPRING_DATASOURCE_PASSWORD` | *(vacio)* | Credencial de acceso a base de datos |

---

## 5. Compilacion y Despliegue

```bash
mvn -f ms-rutaexpress-catalog/pom.xml clean package -DskipTests
java -jar ms-rutaexpress-catalog/target/ms-rutaexpress-catalog-1.0.0.jar
```