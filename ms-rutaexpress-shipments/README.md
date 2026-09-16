# ms-rutaexpress-shipments

Microservicio de dominio responsable del ciclo de vida integral de los envios logísticos en la plataforma RutaExpress. Desarrollado con Spring Boot 3.2.4 y Spring Data JPA.

---

## 1. Responsabilidades del Dominio

- Creacion de solicitudes de despacho y asignacion automatica de numero de seguimiento unico en formato `RTX-YYYYMMDD-XXXX`.
- Calculo del costo de envio basado en la tarifa base del servicio seleccionado y el recargo variable por kilometro recorrido.
- Control de la maquina de estados de los envios y aplicacion estricta de las reglas de transicion operativa.
- Coordinacion directa con `ms-rutaexpress-catalog` para descontar cupos de flota al aceptar un envio y restituirlos en caso de cancelacion posterior.
- Notificacion asincrona desacoplada (`@Async`) hacia `ms-rutaexpress-audit` en cada evento del ciclo de vida para asegurar la persistencia inmutable de la trazabilidad.

---

## 2. Maquina de Estados y Reglas de Negocio

El flujo de estados permitido se define segun el siguiente orden operacional:

```text
[ CREADO ] ---> [ ACEPTADO ] ---> [ EN_BODEGA ] ---> [ EN_RUTA ] ---> [ ENTREGADO ]
    |                |                  |                 |
    +----------------+------------------+-----------------+-------> [ CANCELADO ]
```

### Reglas Operativas Clave:
1. **Validacion de Despacho en Ruta**: No esta permitido transicionar un envio al estado `EN_RUTA` si este no ha pasado de manera explicita y previa por el estado `ACEPTADO`. Intentar dicha transicion devuelve un error HTTP 400 Bad Request con mensaje descriptivo de la restriccion violada.
2. **Consumo de Flota**: Cuando el estado cambia a `ACEPTADO`, el microservicio invoca el endpoint de reserva de `ms-rutaexpress-catalog`. Si el catalogo responde con capacidad insuficiente, la transicion es abortada.
3. **Restitucion por Cancelacion**: Si un envio en estado `ACEPTADO`, `EN_BODEGA` o `EN_RUTA` es cancelado, se emite una solicitud al catalogo para reponer el cupo de capacidad liberado.
4. **Estados Terminales**: Los estados `ENTREGADO` y `CANCELADO` son inmutables; no permiten transiciones subsecuentes.

---

## 3. Endpoints del Servicio

| Metodo | Ruta | Descripcion | Codigo Exito |
|---|---|---|---|
| `POST` | `/api/shipments` | Registra un nuevo envio en estado inicial `CREADO` | 201 Created |
| `GET` | `/api/shipments/{id}` | Retorna el detalle completo de un envio por ID | 200 OK |
| `GET` | `/api/shipments/tracking/{code}` | Consulta de envio mediante codigo de seguimiento | 200 OK |
| `PUT` | `/api/shipments/{id}/status` | Aplica una transicion de estado validando reglas de negocio | 200 OK |
| `GET` | `/api/shipments` | Consulta filtrada por estado (`status`), fecha desde (`from`) y hasta (`to`) | 200 OK |

---

## 4. Ejemplos de Intercambio de Datos

### Peticion: Creacion de Envio (`POST /api/shipments`)
```json
{
  "serviceId": 1,
  "senderName": "Juan Remitente",
  "senderAddress": "Av. Apoquindo 4500, Las Condes",
  "senderPhone": "+56911223344",
  "recipientName": "Maria Destino",
  "recipientAddress": "Av. Libertad 300, Vina del Mar",
  "recipientEmail": "maria@correo.cl",
  "recipientPhone": "+56988776655",
  "weightKg": 2.5,
  "distanceKm": 115.0,
  "declaredValue": 35000.00,
  "createdBy": "cliente@correo.cl",
  "notes": "Fragil"
}
```

### Peticion: Actualizacion de Estado (`PUT /api/shipments/{id}/status`)
```json
{
  "status": "ACEPTADO",
  "performedBy": "operador.despacho@rutaexpress.cl",
  "userRole": "Despachador",
  "note": "Aceptado para recepcion en turno matutino"
}
```

---

## 5. Variables de Entorno

| Variable | Valor por Defecto | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8081` | Puerto HTTP del servicio |
| `CATALOG_SERVICE_URL` | `http://localhost:8082` | URL del servicio de catalogo |
| `AUDIT_SERVICE_URL` | `http://localhost:8083` | URL del servicio de auditoria |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:shipmentsdb` | Cadena de conexion a base de datos |

---

## 6. Pruebas Unitarias

Para ejecutar las pruebas que validan el comportamiento de la maquina de estados:
```bash
mvn -f ms-rutaexpress-shipments/pom.xml test
```