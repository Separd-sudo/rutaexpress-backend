# Arquitectura Técnica - RutaExpress Backend

## 1. Visión General de la Solución

RutaExpress es una plataforma Cloud-Native diseñada para la gestión logística de envíos de última milla. El backend está compuesto por 3 microservicios de dominio más un Backend-For-Frontend (BFF), orquestados con Docker y preparados para integración híbrida con Azure AD y AWS.

```
                  +-------------------------------+
                  |      Frontend React (MSAL)    |
                  +---------------+---------------+
                                  |
                   HTTPS (Bearer JWT con App Roles)
                                  v
                  +---------------+---------------+
                  |  AWS API Gateway (HTTP API)  |
                  |     (JWT Authorizer Azure)    |
                  +---------------+---------------+
                                  |
                           Puerto :8080
                                  v
                  +---------------+---------------+
                  |      ms-rutaexpress-bff       |
                  |  (Spring Security + Gateway)  |
                  +-------+-------+-------+-------+
                          |       |       |
            +-------------+       |       +-------------+
            | :8081               | :8082               | :8083
            v                     v                     v
+-----------------------+ +--------------------+ +---------------------+
| ms-rutaexpress-       | | ms-rutaexpress-    | | ms-rutaexpress-     |
| shipments             | | catalog            | | audit               |
| (Gestión de Envíos)   | | (Servicios y Flota)| | (Trazabilidad)      |
+-----------+-----------+ +---------+----------+ +----------+----------+
            |                       ^                       ^
            |  Descuenta capacidad  |                       |
            +-----------------------+                       |
            |                                               |
            +------------ Registro asíncrono de eventos ----+
```

---

## 2. Componentes del Backend

### 2.1 ms-rutaexpress-bff (Puerto 8080)
- **Tecnología**: Spring Boot 3.2.4, Spring Security, OAuth2 Resource Server.
- **Responsabilidad**:
  - Punto de entrada unificado para clientes HTTP.
  - Validación de tokens JWT emitidos por Microsoft Entra ID (Azure AD).
  - Mapeo de App Roles (`Admin`, `Despachador`, `Cliente`, `Auditor`).
  - Endpoint de agregación `GET /api/bff/shipments/{id}/full-trace` que consolida datos de envío, catálogo y timeline en una sola llamada optimizada para React.

### 2.2 ms-rutaexpress-shipments (Puerto 8081)
- **Tecnología**: Spring Boot 3.2.4, Spring Data JPA, H2 / PostgreSQL.
- **Responsabilidad**:
  - Ciclo de vida completo del envío: creación, asignación de tracking, actualización de estados.
  - Cálculo de tarifas según distancia y peso.
- **Máquina de Estados y Reglas Clave**:
  - Estados: `CREADO` -> `ACEPTADO` -> `EN_BODEGA` -> `EN_RUTA` -> `ENTREGADO` (o `CANCELADO`).
  - **Regla 1**: No se puede pasar a `EN_RUTA` sin haber sido `ACEPTADO` previamente.
  - **Regla 2**: Al pasar a `ACEPTADO`, invoca a `ms-rutaexpress-catalog` para descontar 1 cupo de la capacidad diaria de flota.
  - **Regla 3**: Si un envío aceptado se cancela, se restituye la capacidad en catálogo.
  - **Regla 4**: En cada cambio de estado, emite un evento de auditoría de forma asíncrona hacia `ms-rutaexpress-audit`.

### 2.3 ms-rutaexpress-catalog (Puerto 8082)
- **Tecnología**: Spring Boot 3.2.4, Spring Data JPA, H2 / PostgreSQL.
- **Responsabilidad**:
  - Catálogo de tipos de envío (Express Mismo Día, Estándar Día Siguiente, Económico).
  - Control de tarifas base y variables por km.
  - Gestión transaccional de capacidad de flota diaria disponible.
  - Endpoints atómicos: `POST /api/catalog/services/{id}/reserve-capacity` y `release-capacity`.

### 2.4 ms-rutaexpress-audit (Puerto 8083)
- **Tecnología**: Spring Boot 3.2.4, Spring Data JPA, H2 / PostgreSQL.
- **Responsabilidad**:
  - Almacén de eventos inmutables de trazabilidad.
  - Guarda: `eventId`, `shipmentId`, `trackingNumber`, `eventType`, `previousStatus`, `newStatus`, `performedBy`, `userRole`, `ipAddress`, `timestamp`, `details`.
  - Consultas rápidas indexadas por envío (`/api/audit/shipments/{id}`) y filtros multicriterio (`/api/audit?user=...&from=...&to=...`).

---

## 3. Matriz de Endpoints Esenciales

| Microservicio | Método | Ruta | Descripción |
|---|---|---|---|
| **BFF** | `GET` | `/api/bff/shipments/{id}/full-trace` | Agregación completa (envío + catálogo + auditoría) |
| **BFF** | `GET` | `/api/bff/health` | Healthcheck del ecosistema |
| **Shipments** | `POST` | `/api/shipments` | Crear envío (estado `CREADO`) |
| **Shipments** | `GET` | `/api/shipments/{id}` | Obtener envío por ID |
| **Shipments** | `GET` | `/api/shipments/tracking/{code}` | Obtener envío por número de seguimiento |
| **Shipments** | `PUT` | `/api/shipments/{id}/status` | Cambiar estado (valida reglas de negocio) |
| **Shipments** | `GET` | `/api/shipments` | Listar envíos por estado y fechas |
| **Catalog** | `GET` | `/api/catalog/services` | Listar servicios y capacidad disponible |
| **Catalog** | `POST` | `/api/catalog/services` | Crear servicio |
| **Catalog** | `PUT` | `/api/catalog/services/{id}` | Modificar tarifa/capacidad |
| **Catalog** | `POST` | `/api/catalog/services/{id}/reserve-capacity` | Descontar capacidad de flota |
| **Audit** | `POST` | `/api/audit/events` | Registrar evento de auditoría |
| **Audit** | `GET` | `/api/audit/shipments/{id}` | Timeline cronológico de un envío |
| **Audit** | `GET` | `/api/audit` | Búsqueda filtrada de eventos |