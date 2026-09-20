# Microservicio de Envios - ms-rutaexpress-shipments

Microservicio de dominio responsable del ciclo de vida integral de las solicitudes de despacho, la asignacion de codigos de seguimiento y la aplicacion estricta de las reglas de transicion operativa en RutaExpress.

---

## 1. Funcion del Microservicio

Este componente actua como el motor central logistico del negocio. Se encarga de:
- Registrar nuevas ordenes de envio y generar su codigo de seguimiento unico con formato `RTX-YYYYMMDD-XXXX`.
- Calcular el costo total de cada envio segun el peso, la distancia en kilometros y la tarifa del servicio consultado en el catalogo.
- Controlar las transiciones de estado de los paquetes para asegurar que ningun envio sea despachado sin autorizacion previa.
- Coordinar con `ms-rutaexpress-catalog` la reserva y devolucion de cupos de vehiculos de forma automatica.
- Notificar asincronamente cada cambio a `ms-rutaexpress-audit` para dejar constancia de auditoria.

---

## 2. Maquina de Estados y Reglas de Negocio

El flujo de estados sigue un orden secuencial obligatorio:

```text
[ CREADO ] ---> [ ACEPTADO ] ---> [ EN_BODEGA ] ---> [ EN_RUTA ] ---> [ ENTREGADO ]
    |                |                  |                 |
    +----------------+------------------+-----------------+-------> [ CANCELADO ]
```

### Reglas Operativas Estrictas:
1. **Regla de Despacho en Ruta**: Ningun envio puede pasar al estado `EN_RUTA` si antes no paso por `ACEPTADO`. Si se intenta saltar este paso, el sistema responde con error HTTP 400 Bad Request.
2. **Reserva de Cupo de Flota**: Al pasar al estado `ACEPTADO`, el servicio descuenta 1 cupo disponible en el microservicio de catalogo. Si no hay vehiculos disponibles, la operacion es rechazada con HTTP 409 Conflict.
3. **Devolucion por Cancelacion**: Si un envio aceptado, en bodega o en ruta es cancelado, se notifica al catalogo para reponer el cupo a la flota.
4. **Estados Finales**: Los estados `ENTREGADO` y `CANCELADO` son inmutables; no permiten modificaciones posteriores.

---

## 3. Base de Datos y Persistencia

- Motor: PostgreSQL 15 o superior.
- Base de datos asignada: `rutaexpress_shipments`.
- Tablas gestionadas: `shipments` (almacena remitente, destinatario, dimensiones, costo, estado actual y fechas de operacion).

---

## 4. Endpoints Disponibles

Puerto de ejecucion por defecto: `8081`

| Metodo HTTP | Ruta | Descripcion | Parametros / Body |
|---|---|---|---|
| POST | `/api/shipments` | Crea un nuevo envio en estado CREADO | JSON con datos del remitente, destinatario, peso, distancia y servicio |
| GET | `/api/shipments/{id}` | Consulta el envio por su ID numerico | ID en la ruta |
| GET | `/api/shipments/tracking/{code}` | Consulta el envio por su codigo de seguimiento | Codigo en la ruta (ej. `RTX-20260916-0001`) |
| PUT | `/api/shipments/{id}/status` | Cambia el estado del envio validando reglas de negocio | JSON con `status`, `performedBy`, `userRole` y `note` |
| GET | `/api/shipments` | Consulta envios con filtros | `?status=ACEPTADO&from=...&to=...` |
| GET | `/actuator/health` | Estado de salud del microservicio | Ninguno |
| GET | `/swagger-ui.html` | Interfaz interactiva de documentacion Swagger | Ninguno |

---

## 5. Variables de Configuracion

| Variable de Entorno | Valor por Defecto | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8081` | Puerto TCP donde escucha el servicio |
| `CATALOG_SERVICE_URL` | `http://localhost:8082` | Direccion de conexion a ms-rutaexpress-catalog |
| `AUDIT_SERVICE_URL` | `http://localhost:8083` | Direccion de conexion a ms-rutaexpress-audit |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/rutaexpress_shipments` | URL JDBC de conexion a PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Usuario de base de datos |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Contrasena de base de datos |

---

## 6. Como Levantar este Microservicio

### Requisitos previos:
- Java JDK 17 instalado.
- Servidor PostgreSQL activo con la base de datos `rutaexpress_shipments` creada.
- El microservicio de catalogo (`8082`) y auditoria (`8083`) deben estar activos para la coordinacion de negocio.

### Opcion A: Ejecutar con Maven (Modo Desarrollo)
```bash
mvn -pl ms-rutaexpress-shipments spring-boot:run
```

### Opcion B: Compilar el JAR y Ejecutar
1. Compilar:
```bash
mvn clean package -pl ms-rutaexpress-shipments -DskipTests
```
2. Ejecutar:
```bash
java -jar ms-rutaexpress-shipments/target/ms-rutaexpress-shipments-1.0.0.jar
```

### Opcion C: Ejecutar con Docker
```bash
docker build -t ms-rutaexpress-shipments:1.0.0 ./ms-rutaexpress-shipments
docker run -d -p 8081:8081 --name shipments-svc ms-rutaexpress-shipments:1.0.0
```