# Backend For Frontend - ms-rutaexpress-bff

Microservicio de fachada y agregacion de servicios desarrollado con Spring Boot 3.2.4 y Spring Security. Actua como puerta de entrada unica para las aplicaciones clientes (React SPA) o para el API Gateway en la nube.

---

## 1. Funcion del Microservicio

En lugar de que el frontend tenga que llamar a 3 microservicios distintos y lidiar con 3 puertos diferentes, el BFF ofrece un unico punto de contacto:
- Enrutamiento: Redirige las peticiones entrantes hacia el microservicio correspondiente (Shipments, Catalog o Audit).
- Agregacion de datos: En el endpoint `GET /api/bff/shipments/{id}/full-trace`, combina la informacion del envio, el catalogo de tarifas y el historial de auditoria en una sola respuesta JSON, reduciendo el numero de peticiones en red.
- Validacion de Seguridad: Integra Spring Security OAuth2 Resource Server para verificar tokens JWT corporativos emitidos por Microsoft Entra ID (Azure AD) o AWS Cognito.
- Conversion de Roles: Transforma los permisos del token en roles de Spring Security (`Admin`, `Despachador`, `Cliente`, `Auditor`) mediante `JwtRoleConverter`.
- Soporte CORS: Permite peticiones directas desde navegadores en entornos de desarrollo local y produccion.

---

## 2. Endpoints Disponibles

Puerto de ejecucion por defecto: `8080`

| Metodo HTTP | Ruta | Descripcion | Permiso Requerido |
|---|---|---|---|
| GET | `/api/bff/health` | Comprobacion de salud del BFF | Publico |
| GET | `/api/bff/catalog/services` | Lista de servicios y cupos de flota | Publico |
| GET | `/api/bff/catalog/services/{id}` | Informacion detallada de un servicio | Publico |
| POST | `/api/bff/catalog/services` | Creacion de servicio tarifario | Admin |
| PUT | `/api/bff/catalog/services/{id}` | Actualizacion de tarifas y cupos | Admin |
| POST | `/api/bff/shipments` | Creacion de solicitud de despacho | Cliente, Despachador, Admin |
| GET | `/api/bff/shipments/{id}` | Consulta de envio por ID | Todos los roles |
| GET | `/api/bff/shipments/tracking/{code}` | Consulta de envio por codigo de rastreo | Todos los roles |
| PUT | `/api/bff/shipments/{id}/status` | Cambio de estado de un envio | Despachador, Admin |
| GET | `/api/bff/shipments` | Listado de envios con filtros | Todos los roles |
| GET | `/api/bff/shipments/{id}/full-trace` | Consulta unificada (Envio + Catalogo + Auditoria) | Todos los roles |
| GET | `/api/bff/audit/shipments/{id}` | Historial de auditoria de un envio | Auditor, Admin |
| GET | `/api/bff/audit` | Busqueda global de eventos de auditoria | Auditor, Admin |
| GET | `/swagger-ui.html` | Interfaz interactiva de documentacion Swagger | Publico |

---

## 3. Variables de Configuracion

| Variable de Entorno | Valor por Defecto | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto TCP donde escucha el BFF |
| `SECURITY_AZURE_ENABLED` | `false` | Activa la validacion estricta de tokens JWT. En `false` permite pruebas locales |
| `AZURE_JWT_ISSUER_URI` | `https://login.microsoftonline.com/common/v2.0` | URL del emisor de tokens JWT de Azure AD |
| `AZURE_JWK_SET_URI` | `https://login.microsoftonline.com/common/discovery/v2.0/keys` | URL de claves publicas para verificar la firma del token |
| `SHIPMENTS_SERVICE_URL` | `http://localhost:8081` | Direccion de conexion a ms-rutaexpress-shipments |
| `CATALOG_SERVICE_URL` | `http://localhost:8082` | Direccion de conexion a ms-rutaexpress-catalog |
| `AUDIT_SERVICE_URL` | `http://localhost:8083` | Direccion de conexion a ms-rutaexpress-audit |

---

## 4. Como Levantar este Microservicio

### Requisitos previos:
- Java JDK 17 instalado.
- Los microservicios de Catalog (`8082`), Shipments (`8081`) y Audit (`8083`) deben estar activos para responder a las consultas.

### Opcion A: Ejecutar con Maven (Modo Desarrollo)
```bash
mvn -pl ms-rutaexpress-bff spring-boot:run
```

### Opcion B: Compilar el JAR y Ejecutar
1. Compilar:
```bash
mvn clean package -pl ms-rutaexpress-bff -DskipTests
```
2. Ejecutar:
```bash
java -jar ms-rutaexpress-bff/target/ms-rutaexpress-bff-1.0.0.jar
```

### Opcion C: Ejecutar con Docker
```bash
docker build -t ms-rutaexpress-bff:1.0.0 ./ms-rutaexpress-bff
docker run -d -p 8080:8080 --name bff-svc ms-rutaexpress-bff:1.0.0
```