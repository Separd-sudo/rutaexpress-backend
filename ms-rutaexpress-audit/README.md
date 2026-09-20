# Microservicio de Auditoria - ms-rutaexpress-audit

Microservicio transversal responsable del almacenamiento inmutable y consulta de la trazabilidad historica de todos los eventos operativos ocurridos en la plataforma RutaExpress.

---

## 1. Funcion del Microservicio

En operaciones logisticas es critico saber con exactitud quien, cuando y que cambio se realizo sobre un envio. Este microservicio cumple esa labor:
- Ingesta eventos generados por los demas servicios (creacion de envio, cambios de estado, reservas de flota).
- Genera identificadores unicos de evento con prefijo `EVT-UUID`.
- Garantiza la inmutabilidad: las operaciones son exclusivamente de adicion (append-only); ningun evento puede ser editado ni borrado de la base de datos.
- Ofrece consultas cronologicas para reconstruir la historia completa de un paquete desde su recepcion hasta su entrega.

---

## 2. Base de Datos y Persistencia

- Motor: PostgreSQL 15 o superior.
- Base de datos asignada: `rutaexpress_audit`.
- Tablas gestionadas: `audit_events` (registra ID de envio, codigo de seguimiento, tipo de evento, estado anterior, estado nuevo, usuario responsable, rol del operador, direccion IP y marca temporal).

---

## 3. Endpoints Disponibles

Puerto de ejecucion por defecto: `8083`

| Metodo HTTP | Ruta | Descripcion | Parametros / Body |
|---|---|---|---|
| POST | `/api/audit/events` | Registra un nuevo evento inmutable en el historico | JSON con datos de la accion realizada |
| GET | `/api/audit/shipments/{shipmentId}` | Obtiene la linea de tiempo cronologica de un envio | ID del envio en la ruta |
| GET | `/api/audit` | Busca eventos segun filtros de auditoria | `?user=...&eventType=...&from=...&to=...` |
| GET | `/actuator/health` | Estado de salud y conexion a la base de datos | Ninguno |
| GET | `/swagger-ui.html` | Interfaz interactiva de documentacion Swagger | Ninguno |

---

## 4. Variables de Configuracion

| Variable de Entorno | Valor por Defecto | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8083` | Puerto TCP donde escucha el servicio |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/rutaexpress_audit` | URL JDBC de conexion a PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Usuario de base de datos |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Contrasena de base de datos |

---

## 5. Como Levantar este Microservicio

### Requisitos previos:
- Java JDK 17 instalado.
- Servidor PostgreSQL activo con la base de datos `rutaexpress_audit` creada.

### Opcion A: Ejecutar con Maven (Modo Desarrollo)
```bash
mvn -pl ms-rutaexpress-audit spring-boot:run
```

### Opcion B: Compilar el JAR y Ejecutar
1. Compilar:
```bash
mvn clean package -pl ms-rutaexpress-audit -DskipTests
```
2. Ejecutar:
```bash
java -jar ms-rutaexpress-audit/target/ms-rutaexpress-audit-1.0.0.jar
```

### Opcion C: Ejecutar con Docker
```bash
docker build -t ms-rutaexpress-audit:1.0.0 ./ms-rutaexpress-audit
docker run -d -p 8083:8083 --name audit-svc ms-rutaexpress-audit:1.0.0
```