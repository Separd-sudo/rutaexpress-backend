# ms-rutaexpress-bff

Microservicio Backend-For-Frontend (BFF) desarrollado con Spring Boot 3.2.4 y Spring Security. Actua como fachada y punto de enlace unico para clientes web (React SPA) o moviles detras de AWS API Gateway, abstrayendo la topologia interna de microservicios de dominio.

---

## 1. Responsabilidades

- Gestion centralizada de seguridad mediante OAuth2 Resource Server para validar tokens JWT emitidos por Microsoft Entra ID (Azure AD).
- Transformacion de claims de identidad mediante `JwtRoleConverter` para mapear los roles corporativos (`Admin`, `Despachador`, `Cliente`, `Auditor`) al contexto de seguridad de Spring (`ROLE_*`).
- Configuracion permisiva de CORS para clientes basados en React (puertos de desarrollo 3000, 5173 y dominios de produccion).
- Agregacion de datos en el endpoint `GET /api/bff/shipments/{id}/full-trace`, unificando la entidad de envio, las especificaciones del catalogo de servicio y la linea de tiempo cronologica de auditoria en una unica respuesta JSON optimizada.
- Enrutamiento y reenvio de peticiones hacia los microservicios internos de dominio.

---

## 2. Matriz de Endpoints

| Metodo | Ruta | Descripcion | Rol Minimo Requerido |
|---|---|---|---|
| `GET` | `/api/bff/health` | Comprobacion de salud del servicio | Publico |
| `GET` | `/api/bff/catalog/services` | Listado de servicios y capacidades de flota | Publico / Todos |
| `GET` | `/api/bff/catalog/services/{id}` | Detalle de un servicio de catalogo | Publico / Todos |
| `POST` | `/api/bff/catalog/services` | Creacion de servicio de catalogo | `Admin` |
| `PUT` | `/api/bff/catalog/services/{id}` | Modificacion de tarifas y cupos | `Admin` |
| `POST` | `/api/bff/shipments` | Creacion de solicitud de envio | `Cliente`, `Despachador`, `Admin` |
| `GET` | `/api/bff/shipments/{id}` | Consulta de envio por identificador | `Cliente`, `Despachador`, `Admin`, `Auditor` |
| `GET` | `/api/bff/shipments/tracking/{code}` | Consulta por codigo de seguimiento | `Cliente`, `Despachador`, `Admin`, `Auditor` |
| `PUT` | `/api/bff/shipments/{id}/status` | Actualizacion del estado del envio | `Despachador`, `Admin` |
| `GET` | `/api/bff/shipments` | Listado filtrado de envios | `Cliente`, `Despachador`, `Admin`, `Auditor` |
| `GET` | `/api/bff/shipments/{id}/full-trace` | Agregacion integral (envio + catalogo + timeline) | `Cliente`, `Despachador`, `Admin`, `Auditor` |
| `GET` | `/api/bff/audit/shipments/{id}` | Timeline de auditoria de un envio | `Auditor`, `Admin` |
| `GET` | `/api/bff/audit` | Consulta de eventos con filtros multicriterio | `Auditor`, `Admin` |

---

## 3. Variables de Entorno

| Variable | Valor por Defecto | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto TCP de escucha del servidor |
| `SECURITY_AZURE_ENABLED` | `false` | Activa la validacion estricta de JWT de Azure AD. En `false` permite modo desarrollo local sin token |
| `AZURE_JWT_ISSUER_URI` | `https://login.microsoftonline.com/common/v2.0` | URL del emisor de tokens de Azure Entra ID |
| `AZURE_JWK_SET_URI` | `https://login.microsoftonline.com/common/discovery/v2.0/keys` | URL de claves publicas para verificacion de firma |
| `SHIPMENTS_SERVICE_URL` | `http://localhost:8081` | Direccion base de `ms-rutaexpress-shipments` |
| `CATALOG_SERVICE_URL` | `http://localhost:8082` | Direccion base de `ms-rutaexpress-catalog` |
| `AUDIT_SERVICE_URL` | `http://localhost:8083` | Direccion base de `ms-rutaexpress-audit` |

---

## 4. Ejecucion Local

Compilacion con Maven:
```bash
mvn clean package -DskipTests
```

Ejecucion del paquete JAR:
```bash
java -jar target/ms-rutaexpress-bff-1.0.0.jar
```

Ejemplo de peticion al endpoint de agregacion:
```bash
curl -X GET http://localhost:8080/api/bff/shipments/1/full-trace -H "Accept: application/json"
```