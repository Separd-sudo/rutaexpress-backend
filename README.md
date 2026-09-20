# RutaExpress - Plataforma Logistica Cloud-Native

Plataforma distribuida para la coordinacion logistica y gestion de envios de ultima milla en redes de operadores courier. El sistema esta compuesto por un frontend en React 18 y un backend desacoplado en 4 microservicios con Java 17 y Spring Boot 3.2.4 bajo el patron Backend-For-Frontend (BFF), con persistencia en PostgreSQL y compatibilidad de identidad corporativa en Microsoft Entra ID (Azure AD) y AWS.

---

## 1. Modulos del Sistema

El ecosistema se compone de 5 componentes de software independientes:

| Componente | Puerto | Tecnologia | Descripcion Funcional |
|---|---|---|---|
| `frontend-rutaexpress` | 5173 | React 18 + Vite | Interfaz web de usuario con soporte de inicio de sesion corporativo y perfiles por rol (Admin, Despachador, Cliente, Auditor). |
| `ms-rutaexpress-bff` | 8080 | Spring Boot 3.2.4 | Puerta de entrada unica y agregador de APIs. Valida tokens JWT y enruta peticiones hacia los microservicios internos. |
| `ms-rutaexpress-shipments` | 8081 | Spring Boot 3.2.4 | Nucleo del negocio logistico. Controla el ciclo de vida de los envios, maquina de estados y reglas de despacho. |
| `ms-rutaexpress-catalog` | 8082 | Spring Boot 3.2.4 | Administracion de tarifas y control transaccional de cupos de vehiculos de la flota diaria. |
| `ms-rutaexpress-audit` | 8083 | Spring Boot 3.2.4 | Repositorio inmutable para almacenar la trazabilidad historica de quien, cuando y que cambio se hizo en cada envio. |

---

## 2. Requisitos Previos

Para ejecutar la solucion completa en un entorno local se requiere:
- Java JDK 17 o superior.
- Node.js 18 o superior y gestor de paquetes npm.
- PostgreSQL 15 o superior (o Docker Engine para ejecutar el stack en contenedores).
- Apache Maven 3.9 o superior.

---

## 3. Como Levantar el Sistema por Completo

Existen dos metodos para ejecutar todo el sistema:

### Metodo 1: Ejecucion con Docker Compose (Recomendado para evaluacion completa)

Este metodo arranca automaticamente la base de datos PostgreSQL, inicializa los esquemas y levanta los 4 microservicios en una red privada aislada.

1. En la raiz del proyecto, ejecutar:
```bash
docker compose up -d --build
```

2. Comprobar que los contenedores esten en estado saludable:
```bash
docker compose ps
```

3. Levantar el frontend web en una terminal separada:
```bash
cd frontend-rutaexpress
npm install
npm run dev
```

4. Abrir en el navegador web:
`http://localhost:5173`

---

### Metodo 2: Ejecucion Nativa en Local (Paso a Paso)

Si deseas ejecutar los servicios directamente en tu maquina sin Docker:

#### Paso 2.1: Base de datos PostgreSQL
Asegurarse de tener el servicio de PostgreSQL activo en el puerto 5432 y crear las tres bases de datos requeridas:
```sql
CREATE DATABASE rutaexpress_catalog;
CREATE DATABASE rutaexpress_shipments;
CREATE DATABASE rutaexpress_audit;
```

#### Paso 2.2: Compilar el Backend
En la raiz del proyecto:
```bash
mvn clean package -DskipTests
```

#### Paso 2.3: Iniciar los Microservicios en Orden
Abrir terminales independientes para cada servicio:

1. **Terminal 1 - Catalogo (Puerto 8082)**:
```bash
java -jar ms-rutaexpress-catalog/target/ms-rutaexpress-catalog-1.0.0.jar
```
*Esperar el mensaje Started CatalogServiceApplication.*

2. **Terminal 2 - Auditoria (Puerto 8083)**:
```bash
java -jar ms-rutaexpress-audit/target/ms-rutaexpress-audit-1.0.0.jar
```
*Esperar el mensaje Started AuditServiceApplication.*

3. **Terminal 3 - Envios (Puerto 8081)**:
```bash
java -jar ms-rutaexpress-shipments/target/ms-rutaexpress-shipments-1.0.0.jar
```
*Esperar el mensaje Started ShipmentsServiceApplication.*

4. **Terminal 4 - Backend For Frontend (Puerto 8080)**:
```bash
java -jar ms-rutaexpress-bff/target/ms-rutaexpress-bff-1.0.0.jar
```
*Esperar el mensaje Started BffApplication.*

#### Paso 2.4: Iniciar el Frontend Web
En una quinta terminal:
```bash
cd frontend-rutaexpress
npm run dev
```
Acceder en el navegador a `http://localhost:5173`. En la pantalla de login, seleccionar cualquiera de los roles disponibles para interactuar de inmediato con el backend.

---

## 4. Validacion y Pruebas de Funcionamiento

Se incluye un script automatizado en PowerShell que valida 7 pruebas de integracion extremo a extremo sobre los microservicios activos:

```powershell
.\test-e2e.ps1
```

Este script verifica:
1. Conectividad y respuesta del BFF.
2. Consulta de tarifas y capacidades en el catalogo.
3. Creacion de un nuevo envio.
4. Cumplimiento de la regla de negocio: rechazo del paso directo a EN_RUTA sin autorizacion previa.
5. Transicion a estado ACEPTADO y descuento de cupo en la flota.
6. Flujo completo por bodega, ruta y entrega.
7. Consulta agregada del endpoint de trazabilidad y generacion de eventos de auditoria.

---

## 5. Documentacion Interactiva de APIs

Con los servicios activos, se puede acceder a la documentacion Swagger en:
- BFF (Entrada principal): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Shipments: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- Catalog: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- Audit: [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)

---

## 6. Integracion con Nubes Azure y AWS

Para conocer el procedimiento de despliegue en maquinas virtuales AWS EC2, la configuracion de JWT Authorizer en AWS API Gateway y la federacion de identidades con Microsoft Entra ID o AWS Cognito, consultar la guia detallada en:
[docs/AZURE_AWS_INTEGRATION.md](docs/AZURE_AWS_INTEGRATION.md)