# RutaExpress - Plataforma Backend Cloud-Native

Plataforma backend distribuida para la coordinacion logistica y gestion de envios de ultima milla en redes de operadores courier. La solucion esta desarrollada sobre Java 17 y Spring Boot 3.2.4 bajo un patron de microservicios con un Backend-For-Frontend (BFF), disenada para operar detras de AWS API Gateway y autenticacion corporativa basada en Azure Active Directory (Microsoft Entra ID).

---

## 1. Modulos del Sistema

El ecosistema se divide en 5 componentes de software independientes (Frontend React + 4 servicios Backend):

| Microservicio | Puerto | Dominio | Descripcion Funcional |
|---|---|---|---|
| `frontend-rutaexpress` | 5173 | Frontend SPA | Aplicacion React 18 con MSAL, inicio de sesion con Azure AD / Microsoft Entra ID y panel de operaciones por rol conectado al BFF. |
| `ms-rutaexpress-bff` | 8080 | Gateway / BFF | Punto de entrada unico para clientes web (React), orquestador y agregador de APIs, validador de tokens JWT de Azure AD y control de autorizacion por roles. |
| `ms-rutaexpress-shipments` | 8081 | EnvÃ­os | Control del ciclo de vida de los envios, maquina de estados, calculo de costos, validacion de transiciones y coordinacion con catalogo y auditoria. |
| `ms-rutaexpress-catalog` | 8082 | CatÃ¡logo y Flota | Administracion de tipos de servicio, estructuras tarifarias y gestion transaccional de la capacidad operativa diaria de flota. |
| `ms-rutaexpress-audit` | 8083 | AuditorÃ­a | Repositorio inmutable de eventos de trazabilidad para auditar quien, cuando y desde que rol se opero cada envio. |

> Nota arquitectonica: La comunicacion entre el nucleo de envios, el catalogo y la auditoria se realiza mediante llamadas REST directas desacopladas de forma asincrona (`@Async` y `RestClient`), evitando la sobrecarga operativa de intermediarios como Apache Kafka o RabbitMQ en este entorno.

---

## 2. Requisitos del Entorno

- Java Development Kit (JDK) 17 LTS (Eclipse Adoptium Temurin recomendado)
- Apache Maven 3.9+
- Docker Engine 24+ y Docker Compose v2+
- Git 2.40+

---

## 3. Estructura del Repositorio

```text
.
â”œâ”€â”€ pom.xml                           # POM padre multi-modulo
â”œâ”€â”€ docker-compose.yml                # Despliegue de red y contenedores
â”œâ”€â”€ test-e2e.ps1                      # Suite de pruebas de integracion extremo a extremo
â”œâ”€â”€ docs/
â”‚   â”œâ”€â”€ ARCHITECTURE.md               # Especificacion tecnica y contratos de interfaz
â”‚   â””â”€â”€ AZURE_AWS_INTEGRATION.md      # Guia de enlace Azure AD + AWS API Gateway + EC2
â”œâ”€â”€ ms-rutaexpress-bff/               # Backend For Frontend
â”œâ”€â”€ ms-rutaexpress-shipments/         # Gestion de envios
â”œâ”€â”€ ms-rutaexpress-catalog/           # Catalogo y capacidad de flota
â””â”€â”€ ms-rutaexpress-audit/             # Auditoria y trazabilidad
```

---

## 4. Compilacion y Empaquetado

Para compilar todos los microservicios desde la raiz del repositorio:

```bash
mvn clean package -DskipTests
```

Para ejecutar las pruebas unitarias y de reglas de negocio:

```bash
mvn test
```

---

## 5. Ejecucion con Docker Compose

El archivo `docker-compose.yml` aprovisiona los 4 servicios en una red bridge aislada (`rutaexpress-net`) con comprobaciones de estado de salud (healthchecks) integradas:

```bash
# Construir imagenes y levantar los contenedores en segundo plano
docker compose up -d --build

# Comprobar el estado operativo de los contenedores
docker compose ps

# Monitorear logs consolidados
docker compose logs -f
```

Puertos expuestos hacia el host:
- BFF (Entrada unificada): `http://localhost:8080`
- Shipments API: `http://localhost:8081`
- Catalog API: `http://localhost:8082`
- Audit API: `http://localhost:8083`

Para detener el stack:
```bash
docker compose down
```

---

## 6. Documentacion de APIs (OpenAPI / Swagger)

Cada microservicio expone su documentacion interactiva en las siguientes rutas:

- Swagger UI del BFF: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Swagger UI de Shipments: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- Swagger UI de Catalog: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- Swagger UI de Audit: [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)

---

## 7. Modelo de Seguridad e Integracion Cloud

El sistema soporta integracion con Azure Active Directory (Microsoft Entra ID) para la autenticacion corporativa y autorizacion basada en roles (RBAC):

- `Admin`: Configuracion de servicios de catalogo, asignacion de capacidades y consulta global de operaciones.
- `Despachador`: Recepcion de envios, autorizacion de ingreso a bodega, despacho en ruta y confirmacion de entrega.
- `Cliente`: Registro de nuevas solicitudes de envio y seguimiento mediante numero de tracking.
- `Auditor`: Inspeccion de eventos cronologicos y trazabilidad de cambios de estado (solo lectura).

Para instrucciones detalladas de integracion con AWS API Gateway (HTTP API JWT Authorizer) y aprovisionamiento en AWS EC2, consulte el documento tecnico en [docs/AZURE_AWS_INTEGRATION.md](docs/AZURE_AWS_INTEGRATION.md).

---

## 8. Verificacion Automatizada

Se provee un script en PowerShell para verificar la salud y las reglas de negocio de extremo a extremo:

```powershell
.\test-e2e.ps1
```

Este script valida:
1. Conectividad con el BFF.
2. Consulta del catalogo de tarifas y capacidades.
3. Creacion de envio en estado `CREADO`.
4. Rechazo de la transicion directa a `EN_RUTA` (cumplimiento estricto de la regla de no despacho sin aceptacion).
5. Transicion a `ACEPTADO` con decremento verificado en el catalogo de capacidad de flota.
6. Flujo completo hacia `EN_BODEGA`, `EN_RUTA` y `ENTREGADO`.
7. Consulta del endpoint de agregacion `GET /api/bff/shipments/{id}/full-trace` comprobando la emision de eventos de auditoria.