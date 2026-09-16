# Arquitectura del Sistema - RutaExpress Backend

## 1. Descripcion de la Arquitectura

RutaExpress adopta un patron de microservicios complementado con Backend-For-Frontend (BFF), estructurado para satisfacer los requisitos de desacoplamiento, trazabilidad y escalabilidad de operaciones de despacho de ultima milla.

```text
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
| (Gestion de Envios)   | | (Servicios y Flota)| | (Trazabilidad)      |
+-----------+-----------+ +---------+----------+ +----------+----------+
            |                       ^                       ^
            |  Descuenta capacidad  |                       |
            +-----------------------+                       |
            |                                               |
            +------------ Registro asincrono de eventos ----+
```

---

## 2. Componentes de Software

### 2.1 ms-rutaexpress-bff
- Rol: Gateway de aplicacion y agregador de consultas para clientes web.
- Puerto: 8080.
- Dependencias: Spring Boot Web, Spring Security, OAuth2 Resource Server.
- Funciones:
  - Validacion de tokens criptograficos JWT de Microsoft Entra ID.
  - Conversion de claims mediante `JwtRoleConverter` hacia autoridades `ROLE_*`.
  - Agregacion en `GET /api/bff/shipments/{id}/full-trace` reuniendo la entidad del envio, la configuracion tarifaria del catalogo y el historial de auditoria en una unica transaccion de lectura.

### 2.2 ms-rutaexpress-shipments
- Rol: Nucleo transaccional de gestion de envios.
- Puerto: 8081.
- Dependencias: Spring Boot Web, Spring Data JPA, H2 / PostgreSQL.
- Reglas de negocio:
  - Ciclo de estados: `CREADO` -> `ACEPTADO` -> `EN_BODEGA` -> `EN_RUTA` -> `ENTREGADO` / `CANCELADO`.
  - Prohibicion expresa de transicionar hacia `EN_RUTA` si el envio no fue marcado previamente como `ACEPTADO`.
  - Coordinacion sincrona con el catalogo para restar 1 cupo de flota al aceptar el envio y reponerlo al cancelar.
  - Publicacion desacoplada de eventos hacia auditoria mediante llamadas HTTP asincronas (`@Async`).

### 2.3 ms-rutaexpress-catalog
- Rol: Mantenimiento de tipos de despacho, matrices de calculo y disponibilidad operativa.
- Puerto: 8082.
- Dependencias: Spring Boot Web, Spring Data JPA, H2 / PostgreSQL.
- Reglas de negocio:
  - Validacion de limites maximos diarios de flota.
  - Rechazo con error HTTP 409 Conflict ante solicitudes de reserva cuando la capacidad disponible es cero.

### 2.4 ms-rutaexpress-audit
- Rol: Repositorio inmutable de eventos de auditoria y trazabilidad.
- Puerto: 8083.
- Dependencias: Spring Boot Web, Spring Data JPA, H2 / PostgreSQL.
- Reglas de negocio:
  - Persistencia append-only indexada por identificador de envio y marca de tiempo.
  - Exposicion de endpoints de lectura para fiscalizacion y seguimiento operacional.

---

## 3. Matriz de Endpoints

| Servicio | Metodo | Ruta | Descripcion |
|---|---|---|---|
| **BFF** | `GET` | `/api/bff/shipments/{id}/full-trace` | Consulta unificada de envio, catalogo y timeline |
| **BFF** | `GET` | `/api/bff/health` | Estado de salud del sistema |
| **Shipments** | `POST` | `/api/shipments` | Alta de envio en estado CREADO |
| **Shipments** | `GET` | `/api/shipments/{id}` | Consulta de envio por identificador |
| **Shipments** | `GET` | `/api/shipments/tracking/{code}` | Consulta por codigo de tracking |
| **Shipments** | `PUT` | `/api/shipments/{id}/status` | Actualizacion de estado con validacion de reglas |
| **Shipments** | `GET` | `/api/shipments` | Filtro de envios por estado y rango de fechas |
| **Catalog** | `GET` | `/api/catalog/services` | Listado de servicios y cupos de flota |
| **Catalog** | `POST` | `/api/catalog/services` | Alta de nuevo servicio |
| **Catalog** | `PUT` | `/api/catalog/services/{id}` | Actualizacion de tarifas y limites |
| **Catalog** | `POST` | `/api/catalog/services/{id}/reserve-capacity` | Reduccion de cupo de capacidad |
| **Catalog** | `POST` | `/api/catalog/services/{id}/release-capacity` | Restitucion de cupo de capacidad |
| **Audit** | `POST` | `/api/audit/events` | Registro de evento en historial |
| **Audit** | `GET` | `/api/audit/shipments/{id}` | Historial cronologico de un envio |
| **Audit** | `GET` | `/api/audit` | Busqueda filtrada de eventos |