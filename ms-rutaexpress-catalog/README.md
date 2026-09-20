# Microservicio de Catalogo - ms-rutaexpress-catalog

Microservicio de dominio encargado de la administracion de los servicios logisticos, las tarifas por kilometro y el control transaccional de la capacidad operativa diaria de la flota de transporte.

---

## 1. Funcion del Microservicio

Este componente gestiona la oferta de servicios disponibles para los clientes y asegura que no se despachen mas envios que los permitidos por la flota diaria. 

Funciones principales:
- Registro y consulta de tipos de servicio (ejemplo: Mismo Dia Express, Estandar 24h, Economico).
- Calculo de precios: define la tarifa base y el costo adicional por kilometro recorrido.
- Control de cupos de vehiculos: maneja la capacidad maxima diaria (maxDailyCapacity) y los cupos disponibles en tiempo real (availableCapacity).
- Bloqueo y liberacion atomica de cupos: cuando un envio es aceptado, descuenta 1 cupo; si el envio es cancelado, devuelve el cupo a la flota.

---

## 2. Base de Datos y Persistencia

- Motor: PostgreSQL 15 o superior.
- Base de datos asignada: `rutaexpress_catalog`.
- Esquema de tablas: Hibernate gestiona automaticamente la creacion de la tabla `catalog_services` al iniciar en modo `ddl-auto: update`.
- Datos iniciales: Al arrancar, si la base de datos esta vacia, el servicio precarga automaticamente los servicios base con sus tarifas y cupos operativos.

---

## 3. Endpoints Disponibles

Puerto de ejecucion por defecto: `8082`

| Metodo HTTP | Ruta | Descripcion | Parametros / Body |
|---|---|---|---|
| GET | `/api/catalog/services` | Lista todos los servicios de catalogo | `?activeOnly=true` (opcional) |
| GET | `/api/catalog/services/{id}` | Obtiene el detalle de un servicio por su identificador numerico | ID en la ruta |
| GET | `/api/catalog/services/code/{code}` | Consulta servicio por codigo corto (ejemplo: `SAME_DAY`) | Codigo en la ruta |
| POST | `/api/catalog/services` | Registra un nuevo servicio en el catalogo | JSON con codigo, nombre, precio base, precio por km y cupo diario |
| PUT | `/api/catalog/services/{id}` | Modifica precios o capacidad de un servicio existente | JSON con datos actualizados |
| POST | `/api/catalog/services/{id}/reserve-capacity` | Descuenta cupos disponibles de flota | `?amount=1` |
| POST | `/api/catalog/services/{id}/release-capacity` | Devuelve cupos de flota tras una cancelacion | `?amount=1` |
| GET | `/actuator/health` | Estado de salud y conexion a la base de datos | Ninguno |
| GET | `/swagger-ui.html` | Interfaz interactiva de documentacion Swagger | Ninguno |

---

## 4. Variables de Configuracion

El servicio se puede configurar mediante variables de entorno en el sistema operativo o en contenedores Docker:

| Variable de Entorno | Valor por Defecto | Descripcion |
|---|---|---|
| `SERVER_PORT` | `8082` | Puerto TCP donde escucha el servicio |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/rutaexpress_catalog` | URL de conexion JDBC a PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Usuario administrador de PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Contrasena de PostgreSQL |

---

## 5. Como Levantar este Microservicio

### Requisitos previos:
- Java JDK 17 instalado.
- Servidor PostgreSQL activo en el puerto 5432 con la base de datos `rutaexpress_catalog` creada.

### Opcion A: Ejecutar con Maven (Modo Desarrollo)
Desde la carpeta raiz del proyecto:
```bash
mvn -pl ms-rutaexpress-catalog spring-boot:run
```

### Opcion B: Compilar el JAR y Ejecutar
1. Compilar el archivo empaquetado:
```bash
mvn clean package -pl ms-rutaexpress-catalog -DskipTests
```
2. Ejecutar la aplicacion:
```bash
java -jar ms-rutaexpress-catalog/target/ms-rutaexpress-catalog-1.0.0.jar
```

### Opcion C: Ejecutar con Docker
```bash
docker build -t ms-rutaexpress-catalog:1.0.0 ./ms-rutaexpress-catalog
docker run -d -p 8082:8082 --name catalog-svc --env SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/rutaexpress_catalog ms-rutaexpress-catalog:1.0.0
```