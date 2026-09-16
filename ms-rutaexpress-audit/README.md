# ms-rutaexpress-audit

Microservicio de soporte transversal responsable del registro inmutable de auditoria y trazabilidad cronologica de todas las operaciones logisticas ejecutadas en RutaExpress. Desarrollado con Spring Boot 3.2.4 y Spring Data JPA.

---

## 1. Responsabilidades del Dominio

- Ingesta de eventos operativos generados por el ciclo de vida de los envios y reservas de capacidad de flota.
- Generacion de identificadores unicos de evento con prefijo `EVT-UUID`.
- Garantia de inmutabilidad de los registros: los eventos almacenados no admiten modificacion ni eliminacion (operaciones append-only).
- Exposicion de endpoints de solo lectura para el rol de `Auditor` y `Admin`, permitiendo reconstruir la linea de tiempo cronologica de cualquier envio o auditar la actividad de usuarios especificos.

---

## 2. Endpoints del Servicio

| Metodo | Ruta | Descripcion | Codigo Exito |
|---|---|---|---|
| `POST` | `/api/audit/events` | Registra un nuevo evento de auditoria en el repositorio inmutable | 201 Created |
| `GET` | `/api/audit/shipments/{shipmentId}` | Retorna el timeline cronologico completo de un envio ordenado ascendentemente por fecha | 200 OK |
| `GET` | `/api/audit` | Consulta de eventos con filtros: `user` (operador), `eventType`, `from` y `to` (rango temporal) | 200 OK |

---

## 3. Estructura del Evento de Auditoria

### Registro de Evento (`POST /api/audit/events`)
```json
{
  "shipmentId": 1,
  "trackingNumber": "RTX-20260916-4321",
  "eventType": "STATUS_CHANGED_EN_RUTA",
  "previousStatus": "EN_BODEGA",
  "newStatus": "EN_RUTA",
  "performedBy": "operador.ruta1@rutaexpress.cl",
  "userRole": "Despachador",
  "ipAddress": "192.168.1.50",
  "details": "Envio cargado en furgon patente AB-CD-12 para entrega en zona centro"
}
```

### Respuesta del Timeline (`GET /api/audit/shipments/1`)
```json
[
  {
    "id": 1,
    "eventId": "EVT-8a7f1234-5678-4321-abcd-ef0123456789",
    "shipmentId": 1,
    "trackingNumber": "RTX-20260916-4321",
    "eventType": "SHIPMENT_CREATED",
    "previousStatus": null,
    "newStatus": "CREADO",
    "performedBy": "cliente@correo.cl",
    "userRole": "Cliente",
    "details": "Envio creado en el sistema con servicio SAME_DAY",
    "timestamp": "2026-09-16T14:30:15"
  },
  {
    "id": 2,
    "eventId": "EVT-9b8e2345-6789-5432-bcde-fa1234567890",
    "shipmentId": 1,
    "trackingNumber": "RTX-20260916-4321",
    "eventType": "STATUS_CHANGED_ACEPTADO",
    "previousStatus": "CREADO",
    "newStatus": "ACEPTADO",
    "performedBy": "despachador1@rutaexpress.cl",
    "userRole": "Despachador",
    "details": "Envio aceptado y capacidad de flota reservada",
    "timestamp": "2026-09-16T14:45:00"
  }
]
```

---

## 4. Variables de Entorno

| Variable | Valor por Defecto | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8083` | Puerto HTTP del servicio |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:auditdb` | Cadena de conexion a la base de datos |
| `SPRING_DATASOURCE_USERNAME` | `sa` | Usuario de base de datos |
| `SPRING_DATASOURCE_PASSWORD` | *(vacio)* | Clave de base de datos |

---

## 5. Compilacion y Ejecucion

```bash
mvn -f ms-rutaexpress-audit/pom.xml clean package -DskipTests
java -jar ms-rutaexpress-audit/target/ms-rutaexpress-audit-1.0.0.jar
```