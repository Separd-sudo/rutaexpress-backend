# Documentación Técnica Integral: Arquitectura, APIs, Identidad y Resolución de Incidencias

Este documento detalla el funcionamiento técnico, arquitectónico y operativo del sistema **RutaExpress**, explicando cada uno de los componentes implementados, la federación de identidades multicloud (Microsoft Entra ID y AWS Cognito), el funcionamiento de las APIs y la resolución paso a paso de las incidencias técnicas planteadas.

---

## 1. Conceptos Fundamentales de Identidad y Acceso

### 1.1 ¿Qué es un Tenant (Inquilino) y para qué sirve?
Un **Tenant** (o Inquilino) en **Microsoft Entra ID (Azure AD)** representa una instancia dedicada, aislada y segura de la nube de identidad de Microsoft que pertenece a una organización o empresa específica.

- **Identificador de Directorio (Directory ID / Tenant ID)**: En nuestro proyecto corresponde a `b7bd70fc-34e6-4f0c-80cc-e0909f96d096`. Este UUID garantiza que las solicitudes de autenticación se dirijan estrictamente al directorio de nuestra organización y no a otro.
- **Función principal**:
  1. **Almacenamiento de identidades**: Aloja a los usuarios corporativos, sus contraseñas, métodos de autenticación multifactor (MFA) y perfiles.
  2. **Gobierno y Aislamiento**: Ningún usuario externo de otro tenant puede acceder a los recursos salvo que sea expresamente invitado como usuario externo o federado.
  3. **Gestión de Roles Empresariales (App Roles)**: Dentro del Tenant, la aplicación `RutaExpress-Frontend` define roles institucionales (`Admin`, `Cliente`, `Despachador`, `Auditor`). Cuando un usuario inicia sesión en ese Tenant, Entra ID inyecta estos roles directamente en el token emitido.

### 1.2 ¿Qué es un User Pool de AWS Cognito y para qué sirve?
Un **User Pool** de **Amazon Web Services (AWS)** es un directorio de identidades administrado dentro del ecosistema de AWS.

- **Identificador en el proyecto**: `us-east-1_AkiImfjh5`.
- **Función principal**:
  1. **Directorio y Emisión nativa de tokens**: Permite registrar y autenticar usuarios directamente en AWS emitiendo tokens estándar OIDC (ID Token, Access Token, Refresh Token).
  2. **Broker de Federación de Identidades**: Actúa como puente entre Microsoft Entra ID y los recursos de AWS. Permite que usuarios alojados en el Tenant de Azure inicien sesión en AWS sin necesidad de duplicar contraseñas, intercambiando afirmaciones de identidad a través de protocolos seguros (OIDC / SAML 2.0).
  3. **Aseguramiento de Recursos en AWS**: Permite que herramientas perimetrales como **AWS API Gateway** validen tokens de forma nativa sin tener que consultar a un servidor externo cada vez.

### 1.3 Diferencia y Relación entre Tenant y User Pool en RutaExpress

| Concepto | Microsoft Entra ID (Tenant) | AWS Cognito (User Pool) |
| :--- | :--- | :--- |
| **Rol en el sistema** | Proveedor de Identidad Corporativo (IdP Primario). | Gestor de Identidad y Broker en la Nube de AWS. |
| **Quién vive allí** | Las cuentas corporativas de los colaboradores y usuarios. | Las sesiones federadas y las reglas de integración con AWS. |
| **Firma Criptográfica** | Claves asimétricas publicadas en `login.microsoftonline.com`. | Claves asimétricas publicadas en `cognito-idp.amazonaws.com`. |
| **Propósito de Negocio** | Cumplimiento corporativo: los administradores gestionan bajas y altas de usuarios en un solo lugar centralizado. | Integración cloud-native con infraestructura de AWS (API Gateway, Lambda, EC2). |

---

## 2. Arquitectura de APIs y Microservicios

El backend de RutaExpress utiliza el patrón **BFF (Backend-For-Frontend)** para comunicar la aplicación cliente (React) con un ecosistema de microservicios internos desacoplados.

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

### 2.1 Microservicio BFF (`ms-rutaexpress-bff` - Puerto 8080)
- **Propósito**: Es el único punto de contacto público hacia el backend. Desacopla al frontend de la topología interna de la red de microservicios.
- **Responsabilidades clave**:
  1. **Seguridad Centralizada**: Valida la firma del token JWT recibido mediante Spring Security antes de permitir el paso hacia cualquier microservicio.
  2. **Agregación de Datos**: Provee endpoints combinados (por ejemplo, `/api/bff/shipments/{id}/full-trace`) que consultan en paralelo los envíos, las tarifas del catálogo y el historial de auditoría, entregando una única respuesta optimizada al cliente.
  3. **Enrutamiento y Resiliencia**: Redirige llamadas a `ms-shipments`, `ms-catalog` y `ms-audit` controlando tiempos de espera (*timeouts*), excepciones y formatos unificados.
  4. **Exposición de Salud**: Expone el endpoint `/api/bff/health` que reporta el estado operativo del ecosistema.

### 2.2 Microservicio de Envíos (`ms-rutaexpress-shipments` - Puerto 8081)
- **Propósito**: Gestiona el ciclo de vida operativo de los paquetes y encomiendas.
- **Funcionalidades**:
  - Creación y registro de envíos con cálculo de peso volumétrico.
  - Generación de números de seguimiento únicos (*tracking numbers*).
  - Máquina de estados: `PENDIENTE` -> `EN_TRANSITO` -> `ENTREGADO` (o `CANCELADO`).
  - Endpoint de cancelación y eliminación física de registros (`DELETE /api/shipments/{id}`).

### 2.3 Microservicio de Catálogo (`ms-rutaexpress-catalog` - Puerto 8082)
- **Propósito**: Administra la oferta comercial, tarifas por distancia/peso y tipos de servicio logístico.
- **Funcionalidades**:
  - Consulta de servicios disponibles (`ESTANDAR`, `EXPRESS`, `INTERNACIONAL`).
  - Gestión restringida: Solo usuarios con rol `Admin` pueden crear, actualizar o dar de baja (`DELETE /api/catalog/services/{id}`) servicios del catálogo.

### 2.4 Microservicio de Auditoría (`ms-rutaexpress-audit` - Puerto 8083)
- **Propósito**: Proporciona registro inmutable y trazabilidad de todas las operaciones realizadas en la plataforma.
- **Funcionalidades**:
  - Registro de eventos (`ShipmentEventLog`): Registra qué usuario, con qué rol, en qué fecha/hora y qué acción ejecutó (ej: cambio de estado de envío, creación o eliminación).
  - Consulta de línea de tiempo cronológica por paquete para peritajes o auditorías de control de calidad.

---

## 3. Seguridad de Tokens: Creación, Firma Asimétrica y Validación JWKS

### 3.1 Estructura del Token JWT
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

### 3.2 ¿Cómo y dónde se crea el Token?
El token **no es creado por nuestro backend ni por el frontend**. 
Se crea en los servidores de **Microsoft Entra ID** (o AWS Cognito) tras verificar las credenciales del usuario (o el flujo PKCE). Entra ID toma los datos del usuario, sus roles corporativos, y firma matemáticamente el token con su **clave privada secreta**, la cual nunca sale de los centros de datos de Microsoft.

### 3.3 ¿Qué es el endpoint JWKS y dónde está en el proyecto?
**JWKS (JSON Web Key Set)** es un endpoint público estándar expuesto por el proveedor de identidad que publica una lista de **claves públicas criptográficas**.

#### Ubicación de la URL en la Configuración
En el archivo `ms-rutaexpress-bff/src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${COGNITO_JWK_SET_URI:${AZURE_JWK_SET_URI:https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AkiImfjh5/.well-known/jwks.json}}
```
- Para Azure Entra ID, la URL estándar es:
  `https://login.microsoftonline.com/{tenantId}/discovery/v2.0/keys`
- Para AWS Cognito, la URL estándar es:
  `https://cognito-idp.us-east-1.amazonaws.com/{userPoolId}/.well-known/jwks.json`

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

### 3.4 Conversión de Roles (`JwtRoleConverter`)
En `ms-rutaexpress-bff/src/main/java/com/rutaexpress/bff/config/JwtRoleConverter.java`, el sistema extrae el listado del claim `roles` del token y le agrega el prefijo `ROLE_` requerido por Spring Security (`ROLE_Admin`, `ROLE_Cliente`, etc.). Esto permite proteger los endpoints con reglas granulares como:

```java
.requestMatchers(HttpMethod.DELETE, "/api/bff/catalog/**").hasRole("Admin")
.requestMatchers(HttpMethod.PUT, "/api/bff/shipments/*/status").hasAnyRole("Despachador", "Admin")
```

---

## 4. Análisis y Explicación de las Incidencias Técnicas

### 4.1 Incidencia 1: Error de Política COOP y `window.closed` en Navegador
**Mensaje observado en la consola de depuración:**
```
Cross-Origin-Opener-Policy policy would block the window.closed call.
(anonymous) @ @azure_msal-browser.js:12495
```

#### Causa Raíz
1. **Mecanismo de Login con Ventana Emergente (Popup)**:
   Cuando el frontend utiliza `@azure/msal-browser` con el método `loginPopup()`, la ventana principal de la aplicación React abre una ventana secundaria emergente hacia los servidores de inicio de sesión de Microsoft (`login.microsoftonline.com`).
2. **Relación de Ventanas (`window.opener`)**:
   Para que el flujo funcione, la ventana emergente debe poder comunicarse de vuelta con la ventana original (`window.opener.postMessage()`) y la ventana original debe monitorear periódicamente si la ventana emergente ya se cerró (`popup.closed`).
3. **Restricción de Seguridad COOP (Cross-Origin-Opener-Policy)**:
   Los navegadores con escudos estrictos de privacidad (como Brave Browser o configuraciones con cabeceras `Cross-Origin-Opener-Policy: same-origin`) aíslan los contextos de navegación de diferentes orígenes.
   Al navegar a `login.microsoftonline.com`, el navegador corta el enlace entre la ventana principal y la ventana emergente por protección contra ataques de tipo *Spectre* o *XS-Leaks*. 
   Al quedar roto el enlace, cuando la librería MSAL intenta verificar si la ventana secundaria terminó su labor invocando `popup.closed`, el motor del navegador bloquea la llamada emitiendo la advertencia de COOP.

---

### 4.2 Incidencia 2: `BrowserAuthError: user_cancelled: User cancelled the flow`
**Mensaje observado:**
```
Error en autenticacion Azure AD: BrowserAuthError: user_cancelled: User cancelled the flow.
    at createBrowserAuthError (@azure_msal-browser.js:7978:10)
    at @azure_msal-browser.js:12498:18
```

#### ¿Por qué ocurrió este error?
1. La librería `@azure/msal-browser` mantiene un temporizador y un sondeo activo esperando que la ventana emergente de Microsoft devuelva el código de autorización o el token.
2. Al estar bloqueada la comunicación entre ventanas por los escudos del navegador (COOP), la ventana principal no puede recibir el mensaje de finalización.
3. Si la ventana se cierra prematuramente, pierde el foco o el temporizador de MSAL detecta que el canal de comunicación con el popup se ha desconectado, la librería asume por defecto que el usuario canceló manualmente el inicio de sesión cerrando la ventana.

#### ¿Por qué la aplicación volvía a cargar el inicio dentro de la propia ventana emergente?
- En la configuración de Azure Portal, la URL de redirección configurada para la SPA es `http://localhost:5173/`.
- Al culminar la autenticación con éxito, Microsoft redirige la ventana emergente de vuelta a `http://localhost:5173/#code=...`.
- Si el script de MSAL en la ventana principal ya no tiene el control sobre la ventana secundaria debido al bloqueo del navegador, la ventana emergente no se autodestruye: en su lugar, se comporta como una pestaña normal de navegación y procesa la URL cargando toda la aplicación React de nuevo en miniatura dentro del marco emergente.
- **Solución comprobada**: Al utilizar navegadores estándar como Google Chrome o Microsoft Edge, las políticas de apertura entre orígenes respetan la API de popups para dominios de confianza, permitiendo que MSAL reciba el token, cierre la ventana emergente de forma inmediata y actualice la sesión en la ventana principal sin interrupciones.

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
