# RutaExpress - Plataforma Backend Cloud-Native para Envíos de Última Milla

Backend modular de microservicios desarrollado con **Java 17**, **Spring Boot 3.2.4** y orquestación con **Docker Compose**, diseñado para integrarse con **Azure AD (Microsoft Entra ID)** como proveedor de identidad (IDaaS) y **AWS API Gateway / EC2** para el despliegue e infraestructura cloud.

---

## 📌 Microservicios Implementados

El ecosistema cuenta con 3 microservicios de dominio y un BFF (Backend For Frontend):

| Servicio | Puerto Local | Responsabilidad Principal |
|---|---|---|
| **`ms-rutaexpress-bff`** | `8080` | Punto de entrada unificado, seguridad JWT de Azure AD, CORS para React, agregación de endpoints (`full-trace`). |
| **`ms-rutaexpress-shipments`** | `8081` | Ciclo de vida de envíos (`CREADO` &rarr; `ACEPTADO` &rarr; `EN_BODEGA` &rarr; `EN_RUTA` &rarr; `ENTREGADO` / `CANCELADO`), cálculo de costos y validación de reglas de negocio. |
| **`ms-rutaexpress-catalog`** | `8082` | Catálogo de tipos de envío, tarifas base/km y control transaccional de capacidad diaria de flota. |
| **`ms-rutaexpress-audit`** | `8083` | Almacén inmutable de auditoría y trazabilidad cronológica de eventos logísticos (quién creó, aceptó, despachó, etc.). |

> **Nota de Diseño**: No se utilizan brokers pesados (Kafka / RabbitMQ), la auditoría y control de capacidad se resuelven mediante comunicación REST asíncrona (`@Async` y `RestClient`), haciéndolo ágil, ligero y confiable.

---

## 🚀 Requisitos Previos

- **Java JDK 17** o superior
- **Apache Maven 3.9+**
- **Docker & Docker Compose** (para despliegue en contenedores)
- **Git**

---

## 🛠️ Compilación y Empaquetado

Para compilar y empaquetar todos los microservicios desde la raíz del proyecto:

```bash
mvn clean package -DskipTests
```

Para ejecutar las pruebas unitarias de las reglas de negocio (ej. transición de estados):

```bash
mvn test
```

---

## 🐳 Despliegue con Docker Compose

Para levantar todo el stack de microservicios interconectados en una red interna privada:

```bash
docker compose up -d --build
```

Para verificar el estado y los healthchecks de los contenedores:

```bash
docker compose ps
```

Para ver los logs en tiempo real:

```bash
docker compose logs -f
```

Para detener los servicios:

```bash
docker compose down
```

---

## 📖 Documentación OpenAPI / Swagger

Una vez levantados los servicios, puedes acceder a sus respectivas interfaces interactivas de Swagger:

- **BFF (Gateway)**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Shipments**: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **Catalog**: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- **Audit**: [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)

---

## 🔒 Integración Cloud Híbrida (Azure AD + AWS API Gateway)

Consulta la documentación completa en la carpeta [`docs/`](./docs):
- [**Guía de Integración Azure AD + AWS API Gateway + EC2**](./docs/AZURE_AWS_INTEGRATION.md): Registro de Apps en Entra ID, definición de App Roles (`Admin`, `Despachador`, `Cliente`, `Auditor`), configuración del JWT Authorizer en AWS HTTP API y scripts para EC2.
- [**Documentación de Arquitectura y Contratos de API**](./docs/ARCHITECTURE.md): Diagramas de secuencia, contratos REST y detalle de reglas de negocio.

---

## 🧪 Pruebas de Flujo Extremo a Extremo (E2E)

Se incluye un script automatizado en PowerShell [`test-e2e.ps1`](./test-e2e.ps1) para validar:
1. Creación de un servicio en catálogo con capacidad diaria definida.
2. Creación de un envío con cálculo dinámico de tarifa.
3. Transición a estado `ACEPTADO` (comprobando que la capacidad disponible disminuye en el catálogo).
4. Intento de salto inválido directo a `EN_RUTA` (verificando el rechazo con HTTP 400).
5. Transición regular a `EN_BODEGA`, `EN_RUTA` y `ENTREGADO`.
6. Consulta del timeline cronológico en auditoría y agregación integral en el BFF.

Para ejecutar la prueba:
```powershell
.\test-e2e.ps1
```

---

## 👥 Roles del Sistema

- **`Admin`**: Administra catálogo, tarifas, capacidad de flota y visualiza KPIs globales.
- **`Despachador`**: Acepta envíos entrantes, coordina rutas y actualiza estados de despacho.
- **`Cliente`**: Crea solicitudes de envío y realiza el seguimiento de su tracking.
- **`Auditor`**: Consulta solo de lectura la línea de tiempo (timeline) y registros de trazabilidad.