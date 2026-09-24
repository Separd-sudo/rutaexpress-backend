# Guia Tecnica de Defensa y Arquitectura: Integracion Multicloud (Azure AD + AWS)

Documento tecnico integral para la presentacion y defensa del proyecto **RutaExpress**. Este informe consolida la arquitectura multicloud implementada, las configuraciones exactas aplicadas en Microsoft Azure y Amazon Web Services (AWS), el desglose funcional de las APIs y microservicios, el flujo de CI/CD automatizado y un banco de preguntas y respuestas para responder con precision tecnica ante la comision evaluadora.

---

## 1. Resumen Ejecutivo del Proyecto

### 1.1 Proposito del Sistema
RutaExpress es una plataforma empresarial para la gestion logistica y despacho de encomiendas de ultima milla. Permite a los clientes cotizar y crear envíos, a los operadores logisticos gestionar la capacidad y estados de la flota en ruta, y a los auditores inspeccionar la trazabilidad inmutable de cada paquete.

### 1.2 Enfoque Arquitectonico Multicloud
- **Capa de Identidad Corporativa**: Microsoft Entra ID (Azure Active Directory) como proveedor de identidad institucional (IdP).
- **Capa de Gestion de Identidades en la Nube**: AWS Cognito User Pools actuando como broker de federacion OIDC.
- **Capa de Enrutamiento y Seguridad Perimetral**: AWS API Gateway (HTTP API) con autorizador JWT.
- **Capa de Computo y Microservicios**: Amazon EC2 ejecutando 4 microservicios en Spring Boot 3 y PostgreSQL mediante Docker Compose.
- **Capa de Presentacion**: Frontend React con Vite y arquitectura de componentes protegidos por rol.
- **Capa de Despliegue Continuo (CI/CD)**: Pipeline automatizado en GitHub Actions con GitHub Container Registry (GHCR).

---

## 2. Configuraciones Implementadas en Microsoft Azure (Entra ID)

### 2.1 Registro de la Aplicacion (App Registration)
- **Nombre**: `cognito federation` / `RutaExpress-Frontend`
- **Application (client) ID**: `1a9272d9-8271-48d5-a823-22b94af30424`
- **Directory (tenant) ID**: `b7bd70fc-34e6-4f0c-80cc-e0909f96d096`
- **Tipo de cuenta**: Solo cuentas en este directorio organizativo (Single tenant corporativo).

### 2.2 Configuracion de Plataforma y Redireccion (Authentication)
1. **Plataforma Single-Page Application (SPA)**:
   - **Redirect URIs configuradas**:
     - `http://localhost:5173`
     - `http://localhost:5173/`
   - **Mecanismo de seguridad**: PKCE (Proof Key for Code Exchange) segun estandar RFC 7636.
   - **Flujos hibridos habilitados**: Access tokens y ID tokens para recepcion de aserciones de identidad.
2. **Plataforma Web (Para federacion con AWS Cognito)**:
   - **Redirect URI**: `https://us-east-1akiimfjh5.auth.us-east-1.amazoncognito.com/oauth2/idpresponse`
   - **Client Secret**: Clave secreta generada en **Certificates & secrets** para el intercambio seguro entre servidores de Azure y AWS Cognito.

### 2.3 Permisos de API (API Permissions)
- `openid`: Permite obtener el identificador unico del usuario y emitir el ID Token.
- `profile`: Acceso a nombre, apellidos y datos de perfil del usuario.
- `email`: Permite extraer la direccion de correo institucional corporativo.
- `User.Read`: Lectura delegada del usuario en Microsoft Graph.

---

## 3. Configuraciones Implementadas en Amazon Web Services (AWS)

### 3.1 Instancia de Servidor (Amazon EC2)
- **Tipo de Instancia**: `t3.small` (2 vCPU, 2 GB de memoria RAM fisica).
- **Sistema Operativo**: Amazon Linux 2023 (Kernel Linux 6.18 x86_64).
- **IP Publica Fija**: IP Elastica asignada: `3.81.237.202` (mantiene la direccion constante independientemente de reinicios).
- **Grupo de Seguridad (Security Group)**:
  - Puerto `22/TCP` (SSH): Abierto para acceso administrativo y despliegue del runner de CI/CD.
  - Puerto `8080/TCP` (HTTP): Abierto (`0.0.0.0/0`) para el Backend-For-Frontend (BFF).
- **Memoria Virtual de Intercambio (Swapfile)**:
  - 2 GB creados en `/swapfile` para asegurar un colchon de 4 GB de memoria efectiva (2 GB RAM + 2 GB Swap), evitando que el OOM-killer de Linux degrade los servicios.

### 3.2 AWS Cognito User Pool
- **User Pool ID**: `us-east-1_AkiImfjh5` (Region `us-east-1`).
- **Dominio Cognito Hosted UI**: `https://us-east-1akiimfjh5.auth.us-east-1.amazoncognito.com`
- **Proveedor de Identidad Federado (IdP)**:
  - Nombre: `AzureAD`
  - Tipo: OpenID Connect (OIDC).
  - Emisor: `https://login.microsoftonline.com/b7bd70fc-34e6-4f0c-80cc-e0909f96d096/v2.0`
  - Mapeo de Atributos: `email` de Azure mapped to `email` en Cognito.
- **App Client de Cognito**:
  - Nombre: `rutaexpress-web-client`
  - Tipo: Aplicacion Publica SPA (sin secreto de cliente para ejecucion en navegador).
  - Allowed Callback URLs: `http://localhost:5173/`
  - Allowed OAuth Flows: Authorization code grant (con PKCE).
  - Allowed OAuth Scopes: `openid`, `email`, `profile`.

### 3.3 AWS API Gateway (HTTP API)
- **Nombre**: `rutaexpress-gateway`
- **Protocolo**: HTTP API (baja latencia y menor costo respecto a REST API).
- **Autorizador JWT (JWT Authorizer)**:
  - Emisor: `https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AkiImfjh5`
  - Audiencia: `rutaexpress-web-client`
  - Encabezado de identidad: `$request.header.Authorization` (Bearer token).
- **Ruta e Integracion**:
  - Ruta: `ANY /{proxy+}`
  - Integracion HTTP Proxy apuntando a `http://3.81.237.202:8080/{proxy}`.
  - CORS configurado con metodos `GET, POST, PUT, DELETE, OPTIONS`.

---

## 4. Arquitectura de Microservicios y APIs

El backend esta disenado bajo el patron **Microservicios con Backend-For-Frontend (BFF)**. Cada servicio tiene su propia base de datos aislada en PostgreSQL para garantizar la segregacion de dominios.

### 4.1 Microservicio BFF (`ms-rutaexpress-bff`) - Puerto 8080
- **Rol**: Punto unico de entrada para las aplicaciones cliente. Oculta la topologia interna de la red de contenedores, enruta solicitudes y agrega respuestas complejas.
- **Endpoints Clave**:
  - `GET /api/bff/health`: Comprobacion de salud y disponibilidad.
  - `GET /api/bff/catalog/services`: Lista servicios de despacho y tarifas.
  - `GET /api/bff/shipments`: Consulta y filtrado de envíos.
  - `POST /api/bff/shipments`: Creacion de nuevos envíos.
  - `PUT /api/bff/shipments/{id}/status`: Actualizacion de estado del envío.
  - `GET /api/bff/shipments/{id}/full-trace`: **Endpoint de agregacion**: consulta internamente a Shipments, Catalog y Audit en paralelo y devuelve una respuesta unificada lista para la vista de React.

### 4.2 Microservicio de Catalogo (`ms-rutaexpress-catalog`) - Puerto 8082
- **Rol**: Administracion de tarifas bases, recargo por kilometro y control en tiempo real de la capacidad maxima de la flota vehicular por tipo de servicio.
- **Modelo de Datos (`ServiceCatalog`)**:
  - `code`: Codigo unico (`SAME_DAY`, `NEXT_DAY`, `ECONOMY`).
  - `name`: Nombre descriptivo.
  - `basePrice`: Tarifa minima base.
  - `pricePerKm`: Costo adicional por kilometro recorrido.
  - `maxDailyCapacity`: Capacidad limite de despachos diarios.
  - `availableCapacity`: Cupos disponibles restantes.
- **Endpoints Clave**:
  - `GET /api/catalog/services`: Consulta servicios activos.
  - `POST /api/catalog/services/{id}/reserve`: Descuenta 1 cupo de capacidad cuando un envío es aceptado.
  - `POST /api/catalog/services/{id}/release`: Restituye 1 cupo si un envío es cancelado.

### 4.3 Microservicio de Envios (`ms-rutaexpress-shipments`) - Puerto 8081
- **Rol**: Gestion del ciclo de vida y maquina de estados de los envíos.
- **Maquina de Estados Implementada**:
  - `CREADO`: Envío registrado por el cliente, pendiente de revision.
  - `ACEPTADO`: Validado por el despachador (descuenta cupo en catalogo).
  - `EN_BODEGA`: Paquete almacenado en centro de distribucion.
  - `EN_RUTA`: Paquete cargado en el vehiculo de reparto. **Regla estricta**: solo permitido si fue previamente aceptado.
  - `ENTREGADO`: Estado final de entrega con firma o conformidad.
  - `CANCELADO`: Anulacion del servicio (libera cupo en catalogo).
- **Formula de Calculo de Tarifa**:
  $$\text{Costo Total} = \text{basePrice} + (\text{distancia en Km} \times \text{pricePerKm})$$

### 4.4 Microservicio de Auditoria (`ms-rutaexpress-audit`) - Puerto 8083
- **Rol**: Trazabilidad e inmutabilidad de eventos de negocio para cumplimiento normativo.
- **Modelo de Datos (`AuditEvent`)**:
  - `eventId`: UUID inmutable por evento (`EVT-...`).
  - `shipmentId`: Identificador del envío.
  - `trackingNumber`: Codigo de seguimiento publico.
  - `eventType`: Tipo de accion (`SHIPMENT_CREATED`, `STATUS_CHANGED_...`).
  - `previousStatus` / `newStatus`: Transicion de estados auditada.
  - `performedBy`: Usuario o correo que ejecuto la operacion.
  - `userRole`: Rol con el que actuo (Cliente, Despachador, Admin).
  - `timestamp`: Fecha y hora precisa en que ocurrio el evento.

### 4.5 Base de Datos (`rtx-postgres-db`) - Puerto 5432
- Motor: PostgreSQL 15 Alpine.
- Tres esquemas y bases de datos independientes creadas en el arranque (`01-create-databases.sql`):
  - `rutaexpress_catalog`
  - `rutaexpress_shipments`
  - `rutaexpress_audit`

---

## 5. Pipeline CI/CD Automatizado (GitHub Actions)

El archivo `.github/workflows/deploy.yml` define un flujo totalmente autonomo ante cualquier `push` a las ramas `develop` o `main`:

```
[Push al repositorio]
        │
        ▼
[1. Runner Ubuntu: Compilación Maven (JDK 17)]
        │ Genera app.jar para los 4 microservicios
        ▼
[2. Docker Buildx & Push a GHCR]
        │ Crea imágenes ligeras (eclipse-temurin:17-jre-alpine)
        │ Publica en ghcr.io/separd-sudo/...
        ▼
[3. Despliegue Remoto en EC2 via Native OpenSSH]
        │ Conexión segura usando EC2_SSH_KEY
        │ Configuración de Swapfile de 2GB
        │ Login autenticado en ghcr.io
        │ docker compose pull && docker compose up -d
        ▼
[Microservicios activos y saludables en la nube]
```

### 5.1 Optimizacion de Recursos en EC2
Para garantizar la estabilidad en la maquina virtual:
- **`JAVA_TOOL_OPTIONS=-XX:+UseSerialGC -Xms96m -Xmx192m`**: Reduce el uso de memoria de la JVM a maximo 192 MB por microservicio, permitiendo que los 4 procesos Java coexistan holgadamente en 2 GB de RAM.
- **Docker Compose Healthchecks**: Cada contenedor reporta su estado con `wget -qO- /actuator/health` asegurando que los servicios arranquen en el orden correcto de dependencias.

---

## 6. Banco de Preguntas y Respuestas para la Evaluacion

### P1: ¿Por que utilizaron un patron BFF en lugar de que el frontend llame a cada microservicio?
> **Respuesta**: "El patron BFF (Backend-For-Frontend) actua como fachada y agregador. Si el frontend se conectara directamente a los microservicios, tendria que conocer las IPs y puertos de cada uno, hacer multiples llamadas HTTP para armar una sola pantalla (aumentando la latencia en redes moviles) y estariamos exponiendo puertos internos de base de datos y logica de negocio a internet. Con el BFF, solo exponemos el puerto 8080, permitimos que el backend resuelva las llamadas en la red interna de alta velocidad de Docker y entregamos endpoints optimizados como `/full-trace` en una unica respuesta JSON."

### P2: ¿Como funciona la federacion de identidades entre Azure AD y AWS Cognito?
> **Respuesta**: "Azure AD actua como el Proveedor de Identidad institucional (IdP) donde residen los usuarios corporativos. AWS Cognito actua como un intermediario o Identity Broker. Cuando el usuario decide iniciar sesion con Cognito, es redirigido mediante el protocolo OpenID Connect (OIDC) a la pantalla de consentimiento de Microsoft. Al autenticarse, Microsoft devuelve un ID Token firmado a Cognito, quien valida la firma criptografica mediante el endpoint JWKS de Microsoft, mapea los atributos del usuario (como el correo institucional) y emite un token firmado por Cognito que luego es validado por el API Gateway o el BFF."

### P3: ¿Por que es necesario utilizar PKCE en el Frontend React?
> **Respuesta**: "Las aplicaciones de pagina unica (SPA) como React son clientes publicos: el codigo fuente se ejecuta en el navegador del usuario y no pueden almacenar un secreto de cliente de forma segura. El estandar OAuth 2.0 recomienda de forma mandatoria el uso de PKCE (Proof Key for Code Exchange, RFC 7636). El cliente genera un verificador de codigo secreto y envia un reto criptografico (SHA-256) en la peticion inicial. Al recibir el codigo de autorizacion, envia el verificador original, evitando ataques de interceptacion de codigos de autorizacion."

### P4: ¿Como garantizan la inmutabilidad de la auditoria si los estados del envío cambian frecuentemente?
> **Respuesta**: "El microservicio de auditoria (`ms-rutaexpress-audit`) sigue un patron de solo insercion (*append-only*). Las entidades `AuditEvent` solo tienen metodos de creacion y lectura; no existen endpoints de actualizacion (`PUT`) ni eliminacion (`DELETE`). Cada vez que un envío cambia de estado en `ms-rutaexpress-shipments`, este servicio despacha una llamada sincrona de auditoria registrando el estado anterior, el nuevo estado, la marca de tiempo, el usuario y un UUID unico de evento."

### P5: ¿Que problema de memoria tuvieron en la EC2 y como lo resolvieron arquitecturalmente?
> **Respuesta**: "Inicialmente teniamos una maquina `t2.micro` con 1 GB de RAM sin memoria virtual. Cada aplicacion Spring Boot 3 por defecto intenta reservar 350-512 MB de memoria heap, lo que para 4 microservicios mas PostgreSQL requeria mas de 1.5 GB, provocando que el kernel de Linux activara el Out-Of-Memory killer y bloqueara el demonio SSH. Lo resolvimos en tres niveles:
> 1. Escalamos la instancia a `t3.small` con 2 GB de RAM fisica.
> 2. Implementamos una memoria de intercambio virtual (Swap) de 2 GB en el script de arranque para brindar un colchon de 4 GB.
> 3. Limitamos la JVM de cada microservicio con `-XX:+UseSerialGC -Xms96m -Xmx192m`, fijando el consumo total del backend en ~750 MB estables."

### P6: ¿Como asegura el CI/CD que el despliegue se realice sin intervencion manual?
> **Respuesta**: "El pipeline de GitHub Actions compila los archivos JAR una sola vez en el entorno del runner. Luego construye imagenes Docker ultraligeras basadas en Alpine JRE y las publica en GitHub Container Registry (GHCR). Finalmente, mediante una sesion OpenSSH no interactiva con la clave privada `EC2_SSH_KEY`, la maquina virtual descarga las imagenes actualizadas con `docker compose pull` y las levanta con `docker compose up -d`, manteniendo el despliegue continuo de forma 100% autonoma."
