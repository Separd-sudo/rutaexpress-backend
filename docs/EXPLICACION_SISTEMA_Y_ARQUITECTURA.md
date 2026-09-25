# Documentación Técnica Integral: Arquitectura, APIs, Identidad y Configuración en Microsoft Azure

Este documento detalla el funcionamiento técnico, arquitectónico y operativo del sistema **RutaExpress**, explicando cada uno de los componentes implementados, la federación de identidades multicloud (Microsoft Entra ID y AWS Cognito), el funcionamiento de las APIs y todos los pasos de configuración realizados en Microsoft Azure.

---

## 1. Conceptos Fundamentales de Identidad y Acceso

### 1.1 ¿Qué es un Tenant (Inquilino) y para qué sirve?
Un **Tenant** (o Inquilino) en **Microsoft Entra ID (Azure AD)** representa una instancia dedicada, aislada y segura de la nube de identidad de Microsoft que pertenece a una organización o empresa específica.

- **Identificador de Directorio (Directory ID / Tenant ID)**: En nuestro proyecto corresponde a `b7bd70fc-34e6-4f0c-80cc-e0909f96d096`. Este identificador único garantiza que las solicitudes de autenticación se dirijan estrictamente al directorio de nuestra organización.
- **Función principal**:
  1. **Almacenamiento de identidades**: Aloja a los usuarios corporativos, sus credenciales, métodos de autenticación multifactor (MFA) y perfiles.
  2. **Gobierno y Aislamiento**: Ningún usuario externo de otro tenant puede acceder a los recursos salvo que sea expresamente invitado como usuario externo o federado.
  3. **Gestión de Roles Empresariales (App Roles)**: Dentro del Tenant, la aplicación define roles institucionales (`Admin`, `Cliente`, `Despachador`, `Auditor`). Cuando un usuario inicia sesión en ese Tenant, Entra ID inyecta estos roles directamente en el token emitido.

### 1.2 ¿Qué es un User Pool de AWS Cognito y para qué sirve?
Un **User Pool** de **Amazon Web Services (AWS)** es un directorio de identidades administrado dentro del ecosistema de AWS.

- **Identificador en el proyecto**: `us-east-1_AkiImfjh5`.
- **Función principal**:
  1. **Directorio y Emisión nativa de tokens**: Permite registrar y autenticar usuarios directamente en AWS emitiendo tokens estándar OIDC (ID Token, Access Token, Refresh Token).
  2. **Broker de Federación de Identidades**: Actúa como puente entre Microsoft Entra ID y los recursos de AWS. Permite que usuarios alojados en el Tenant de Azure inicien sesión en AWS sin necesidad de duplicar credenciales, intercambiando afirmaciones de identidad a través de protocolos seguros (OIDC / SAML 2.0).
  3. **Aseguramiento Perimetral en AWS**: Permite que componentes como **AWS API Gateway** validen tokens de forma nativa sin tener que consultar a un servidor externo cada vez.

### 1.3 Diferencia y Relación entre Tenant y User Pool en RutaExpress

| Concepto | Microsoft Entra ID (Tenant) | AWS Cognito (User Pool) |
| :--- | :--- | :--- |
| **Rol en el sistema** | Proveedor de Identidad Corporativo (IdP Primario). | Gestor de Identidad y Broker en la Nube de AWS. |
| **Quién vive allí** | Las cuentas corporativas de los colaboradores y usuarios. | Las sesiones federadas y las reglas de integración con AWS. |
| **Firma Criptográfica** | Claves asimétricas publicadas en `login.microsoftonline.com`. | Claves asimétricas publicadas en `cognito-idp.amazonaws.com`. |
| **Propósito de Negocio** | Cumplimiento corporativo: los administradores gestionan bajas y altas de usuarios en un solo lugar centralizado. | Integración cloud-native con infraestructura de AWS (API Gateway, Lambda, EC2). |

---

## 2. Configuración Paso a Paso Realizada en Microsoft Azure (Entra ID)

Para habilitar la autenticación corporativa, la emisión de roles y la federación con AWS, se realizaron los siguientes pasos en **Microsoft Entra ID (portal.azure.com)**:

### Paso 1: Creación y Acceso al Tenant
1. Se configuró el directorio activo corporativo bajo el identificador de Tenant:
   - **Directory (tenant) ID**: `b7bd70fc-34e6-4f0c-80cc-e0909f96d096`.
2. Se crearon los usuarios de prueba con sus cuentas institucionales para representar los distintos roles de la empresa.

### Paso 2: Registro de la Aplicación (App Registration)
1. En Microsoft Entra ID, se navegó a **App registrations** > **New registration**.
2. Se asignaron los siguientes parámetros:
   - **Nombre**: `cognito federation` / `RutaExpress-Frontend`.
   - **Tipos de cuenta admitidos**: *Accounts in this organizational directory only* (Inquilino único / Single tenant).
3. Tras crear el registro, se obtuvo el identificador de cliente:
   - **Application (client) ID**: `1a9272d9-8271-48d5-a823-22b94af30424`.

### Paso 3: Configuración de Autenticación y Plataformas
En el menú **Authentication**:
1. **Plataforma SPA (Single-Page Application)** para el Frontend React:
   - Se agregaron las URIs de redirección locales:
     - `http://localhost:5173`
     - `http://localhost:5173/`
   - Se habilitó la protección mediante **PKCE** (Proof Key for Code Exchange) según el estándar RFC 7636.
2. **Concesión implícita y flujos híbridos**:
   - Se marcaron las casillas para **Access tokens** e **ID tokens**, permitiendo la recepción de aserciones de identidad por parte de librerías cliente (MSAL).
3. **Plataforma Web (Para integración con AWS Cognito)**:
   - Se agregó la plataforma Web con la URI de retorno del User Pool de AWS:
     `https://us-east-1akiimfjh5.auth.us-east-1.amazoncognito.com/oauth2/idpresponse`

### Paso 4: Generación de Certificados y Secretos (Certificates & Secrets)
1. En el menú **Certificates & secrets** > pestaña **Client secrets**, se creó un nuevo secreto de cliente (*New client secret*).
2. Se registró el valor del secreto para configurarlo en el proveedor OIDC de AWS Cognito, permitiendo el canal de comunicación seguro servidor-a-servidor.

### Paso 5: Definición de Roles de Aplicación (App Roles)
En el menú **App roles**, se crearon los cuatro perfiles de seguridad requeridos por la solución:
- **`Admin`**: Administrador del sistema con control total sobre el catálogo de servicios, tarifas y eliminación de registros.
- **`Cliente`**: Usuario final con facultad para cotizar, crear y consultar sus propios envíos.
- **`Despachador`**: Operador logístico autorizado para actualizar los estados de los paquetes en ruta (`EN_TRANSITO`, `ENTREGADO`).
- **`Auditor`**: Inspector de seguridad con acceso exclusivo de lectura a la línea de tiempo inmutable y eventos de auditoría.

*Cada rol se configuró para asignarse a "Users/Groups" con su nombre exacto en el valor del claim.*

### Paso 6: Asignación de Roles a Usuarios Corporativos
1. Se accedió a **Enterprise applications** > `cognito federation` (o la app correspondiente).
2. En el menú **Users and groups**, se seleccionaron los usuarios del directorio y se les asoció su rol específico (`Admin`, `Cliente`, etc.).
3. Gracias a esto, cuando el usuario inicia sesión, Azure Entra ID inyecta automáticamente el claim `"roles": ["Admin"]` en el token JWT.

### Paso 7: Permisos de API (API Permissions)
En el menú **API permissions**, se concedieron los permisos delegados de Microsoft Graph:
- `openid`: Permite la emisión del ID Token y el uso del estándar OpenID Connect.
- `profile`: Otorga acceso al nombre, apellidos y datos de cuenta del usuario.
- `email`: Permite extraer la dirección de correo institucional corporativo.
- `User.Read`: Permite la lectura del perfil básico del usuario autenticado.
- **Grant admin consent**: Se ejecutó el consentimiento de administrador para asegurar que ningún usuario reciba pantallas de bloqueo de consentimiento en su primer acceso.

### Paso 8: Exposición de API y Configuración de Manifiesto
1. En **Expose an API**, se configuró el URI del identificador de la aplicación: `api://1a9272d9-8271-48d5-a823-22b94af30424`.
2. En el **Manifest**, se verificó que `accessTokenAcceptedVersion` estuviera fijado en `2`, garantizando tokens en formato JWT v2.0 estándar.

---

## 3. Arquitectura de APIs y Microservicios

El backend de RutaExpress utiliza el patrón **BFF (Backend-For-Frontend)** para comunicar la aplicación cliente (React) con los microservicios internos desacoplados.

```
                    ┌─────────────────────────┐
                    │  Frontend React (Vite)  │
                    │   http://localhost:5173 │
                    └────────────┬────────────┘
                                 │ HTTP Bearer Token
                                 ▼
                    ┌─────────────────────────┐
                    │     ms-rutaexpress-bff  │  (Puerto 8080)
                    │   Filtro Spring Security│  Validación JWKS
                    └──────┬─────┬─────┬──────┘
                           │     │     │
         ┌─────────────────┘     │     └─────────────────┐
         ▼                       ▼                       ▼
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│  ms-shipments    │   │   ms-catalog     │   │    ms-audit      │
│  (Puerto 8081)   │   │  (Puerto 8082)   │   │  (Puerto 8083)   │
│  Gestión Envíos  │   │ Tarifas/Servicios│   │ Eventos Inmutables│
└──────────────────┘   └──────────────────┘   └──────────────────┘
```

### 3.1 Microservicio BFF (`ms-rutaexpress-bff` - Puerto 8080)
- **Propósito**: Es el único punto de contacto público hacia el backend. Desacopla al frontend de la topología interna de la red de microservicios.
- **Responsabilidades clave**:
  1. **Seguridad Centralizada**: Valida la firma del token JWT recibido mediante Spring Security antes de permitir el paso hacia cualquier microservicio.
  2. **Agregación de Datos**: Provee endpoints combinados (por ejemplo, `/api/bff/shipments/{id}/full-trace`) que consultan en paralelo los envíos, las tarifas del catálogo y el historial de auditoría, entregando una única respuesta optimizada al cliente.
  3. **Enrutamiento y Resiliencia**: Redirige llamadas a `ms-shipments`, `ms-catalog` y `ms-audit` controlando tiempos de espera (*timeouts*), excepciones y formatos unificados.
  4. **Exposición de Salud**: Expone el endpoint `/api/bff/health` que reporta el estado operativo del ecosistema.

### 3.2 Microservicio de Envíos (`ms-rutaexpress-shipments` - Puerto 8081)
- **Propósito**: Gestiona el ciclo de vida operativo de los paquetes y encomiendas.
- **Funcionalidades**:
  - Creación y registro de envíos con cálculo de peso volumétrico.
  - Generación de números de seguimiento únicos (*tracking numbers*).
  - Máquina de estados: `PENDIENTE` -> `EN_TRANSITO` -> `ENTREGADO` (o `CANCELADO`).
  - Endpoint de cancelación y eliminación física de registros (`DELETE /api/shipments/{id}`).

### 3.3 Microservicio de Catálogo (`ms-rutaexpress-catalog` - Puerto 8082)
- **Propósito**: Administra la oferta comercial, tarifas por distancia/peso y tipos de servicio logístico.
- **Funcionalidades**:
  - Consulta de servicios disponibles (`ESTANDAR`, `EXPRESS`, `INTERNACIONAL`).
  - Gestión restringida: Solo usuarios con rol `Admin` pueden crear, actualizar o dar de baja (`DELETE /api/catalog/services/{id}`) servicios del catálogo.

### 3.4 Microservicio de Auditoría (`ms-rutaexpress-audit` - Puerto 8083)
- **Propósito**: Proporciona registro inmutable y trazabilidad de todas las operaciones realizadas en la plataforma.
- **Funcionalidades**:
  - Registro de eventos (`ShipmentEventLog`): Registra qué usuario, con qué rol, en qué fecha/hora y qué acción ejecutó (ej: cambio de estado de envío, creación o eliminación).
  - Consulta de línea de tiempo cronológica por paquete para peritajes o auditorías de control de calidad.

---

## 4. Seguridad de Tokens: Creación, Firma Asimétrica y Validación JWKS

### 4.1 Estructura del Token JWT
Un token JWT (*JSON Web Token*) consta de tres partes codificadas en Base64Url y separadas por puntos (`.`):

1. **Header**: Contiene el tipo de token y el algoritmo de firma criptográfica:
   ```json
   {
     "alg": "RS256",
     "typ": "JWT",
     "kid": "k-10928374abcdef..."
   }
   ```
   - `alg: RS256`: Algoritmo asimétrico RSA de 2048 bits con hash SHA-256.
   - `kid` (Key ID): Identificador de la clave pública exacta que se utilizó para firmar este token.

2. **Payload (Claims)**: Contiene la información de identidad y los privilegios concedidos:
   - `sub`: Identificador único del usuario.
   - `roles`: Matriz con los roles asignados en el Tenant (`["Admin"]` o `["Cliente"]`).
   - `iss`: Emisor de confianza (`https://login.microsoftonline.com/...` o Cognito).
   - `exp`: Timestamp exacto de expiración de la sesión.

3. **Signature (Firma)**: Es el resultado de aplicar la clave privada del proveedor de identidad sobre la cabecera y el payload.

### 4.2 ¿Cómo y dónde se crea el Token?
El token **no es creado por nuestro backend ni por el frontend**. 
Se crea en los servidores de **Microsoft Entra ID** tras verificar las credenciales del usuario mediante el flujo PKCE. Entra ID toma los datos del usuario, sus roles corporativos, y firma matemáticamente el token con su **clave privada secreta**, la cual nunca sale de los centros de datos de Microsoft.

### 4.3 ¿Qué es el endpoint JWKS y dónde está en el proyecto?
**JWKS (JSON Web Key Set)** es un endpoint público estándar expuesto por el proveedor de identidad que publica una lista de **claves públicas criptográficas**.

#### Ubicación de la URL en la Configuración
En el archivo `ms-rutaexpress-bff/src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${COGNITO_ISSUER_URI:${AZURE_JWT_ISSUER_URI:https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AkiImfjh5}}
          jwk-set-uri: ${COGNITO_JWK_SET_URI:${AZURE_JWK_SET_URI:https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AkiImfjh5/.well-known/jwks.json}}
```
- Para Azure Entra ID, la URL estándar es:
  `https://login.microsoftonline.com/b7bd70fc-34e6-4f0c-80cc-e0909f96d096/discovery/v2.0/keys`
- Para AWS Cognito, la URL estándar es:
  `https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AkiImfjh5/.well-known/jwks.json`

#### Ubicación del Bean en el Código Java
En el archivo `ms-rutaexpress-bff/src/main/java/com/rutaexpress/bff/config/SecurityConfig.java`:

```java
@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:...}")
private String jwkSetUri;

@Bean
public JwtDecoder jwtCognitoDecoder() {
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
}
```

#### ¿Cómo valida Spring Boot el Token?
1. El cliente envía la solicitud con la cabecera HTTP: `Authorization: Bearer <token>`.
2. El filtro de Spring Security intercepta la petición y lee el `kid` (Key ID) en la cabecera del JWT.
3. El componente `NimbusJwtDecoder` consulta el endpoint JWKS (almacenado en memoria caché) y obtiene la **clave pública** correspondiente a dicho `kid`.
4. Mediante operaciones matemáticas de criptografía asimétrica, verifica:
   - Que la firma del token coincida exactamente con la clave pública del emisor.
   - Que el token no haya sido alterado en tránsito (integridad).
   - Que no haya expirado (`exp > now()`).
5. **No se comparte ninguna contraseña o secreto entre el servidor y el proveedor**, garantizando máxima seguridad de estándar industrial.

### 4.4 Conversión de Roles (`JwtRoleConverter`)
En `ms-rutaexpress-bff/src/main/java/com/rutaexpress/bff/config/JwtRoleConverter.java`, el sistema extrae el listado del claim `roles` del token y le agrega el prefijo `ROLE_` requerido por Spring Security (`ROLE_Admin`, `ROLE_Cliente`, etc.). Esto permite proteger los endpoints con reglas granulares como:

```java
.requestMatchers(HttpMethod.DELETE, "/api/bff/catalog/**").hasRole("Admin")
.requestMatchers(HttpMethod.PUT, "/api/bff/shipments/*/status").hasAnyRole("Despachador", "Admin")
```

---

## 5. Resumen del Flujo de Verificación Activa en el Frontend

En la interfaz de usuario se implementó un mecanismo de verificación directa en tiempo real:

1. El usuario inicia sesión y visualiza su tarjeta central con sus datos de sesión, rol institucional y token JWT activo.
2. Al presionar el botón **"Verificar Token con BFF"**:
   - El cliente ejecuta `bffApi.getShipments()` adjuntando el token en la cabecera `Authorization: Bearer <token>`.
   - La petición viaja al endpoint del BFF en Spring Boot.
   - El decodificador `NimbusJwtDecoder` consulta las claves JWKS y valida criptográficamente la firma del token y la vigencia temporal.
   - El convertidor `JwtRoleConverter` extrae los roles y valida que el usuario posea permisos de lectura.
   - El BFF responde con código `HTTP 200 OK`.
3. La interfaz captura la respuesta y presenta en pantalla la confirmación:
   `HTTP 200 OK - Petición autorizada con éxito por el BFF`.

---

## 6. Glosario Rápido para Defensa Técnica

- **OIDC (OpenID Connect)**: Capa de identidad montada sobre OAuth 2.0 que proporciona información verificable del usuario mediante tokens ID Token.
- **OAuth 2.0**: Protocolo estándar de la industria enfocado en delegación de autorización mediante Access Tokens.
- **RS256**: Algoritmo de firma digital asimétrica con clave privada (para firmar en Azure) y clave pública (para verificar en Spring Boot).
- **PKCE (Proof Key for Code Exchange)**: Extensión de seguridad para aplicaciones web de una sola página (SPA) que previene la interceptación de códigos de autorización sin requerir secretos en el cliente.
- **BFF (Backend For Frontend)**: Patrón de diseño donde una capa de backend está diseñada exclusivamente para atender las necesidades y la seguridad de una interfaz de usuario específica.
