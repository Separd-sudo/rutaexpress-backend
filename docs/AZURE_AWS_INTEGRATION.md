# Guia de Integracion en la Nube: Azure Active Directory + AWS API Gateway + AWS Cognito

Especificacion tecnica para la integracion de identidad federada corporativa basada en Microsoft Entra ID (Azure AD), AWS API Gateway (HTTP API) y AWS Cognito User Pools, junto con el despliegue del backend en Amazon EC2.

---

## 1. Arquitectura de Identidad en la Nube

Para conectar la autenticacion corporativa de Microsoft Entra ID con los servicios desplegados en AWS existen dos alternativas reconocidas por la industria:

- Opcion 1: Validacion Directa en AWS API Gateway (Recomendada por la pauta tecnica). El API Gateway valida la firma del token JWT directamente contra las llaves publicas (JWKS) de Microsoft Entra ID sin intermediarios.
- Opcion 2: Federacion con AWS Cognito User Pool. Cognito actua como intermediario de identidad (Identity Broker). El usuario se autentica en Azure AD, Cognito recibe la asercion OIDC y emite un token de Cognito que luego es validado por el backend o el API Gateway.

---

## 2. Opcion 1: Validacion Directa con AWS API Gateway (HTTP API)

### 2.1 Configuracion en Microsoft Entra ID (Azure Portal)
1. Iniciar sesion en Azure Portal (`portal.azure.com`) y entrar a **Microsoft Entra ID**.
2. Ir a **App registrations** > **New registration**:
   - Nombre: `RutaExpress-Backend-API`
   - Tipos de cuenta compatibles: *Cuentas en este directorio organizativo unicamente (Inquilino unico)*.
   - Guardar el registro.
3. En la seccion **Overview**, anotar:
   - Application (client) ID: `<AZURE_CLIENT_ID>` (ejemplo: `b6c4a321-8f8c-4f1a-a7b8-d5026fae76ab`)
   - Directory (tenant) ID: `<AZURE_TENANT_ID>`
4. En el menu **Expose an API**:
   - Application ID URI: `api://<AZURE_CLIENT_ID>`
   - Agregar un Scope llamado: `access_as_user`.
5. En el menu **App roles**, crear los 4 roles requeridos:
   - `Admin`
   - `Despachador`
   - `Cliente`
   - `Auditor`
6. Asignar los roles a los usuarios corporativos en **Enterprise applications** > `RutaExpress-Backend-API` > **Users and groups**.

### 2.2 Configuracion en AWS API Gateway
1. En AWS Console, entrar a **API Gateway** > **Create API** > **HTTP API** (Build).
2. Nombre de la API: `rutaexpress-api`.
3. Ir a **Authorization** > pestaña **Manage authorizers** > **Create**:
   - Tipo de autorizador: `JWT`
   - Nombre: `AzureAD-Authorizer`
   - Identity source: `$request.header.Authorization`
   - Issuer URL: `https://login.microsoftonline.com/<AZURE_TENANT_ID>/v2.0`
   - Audience: `api://<AZURE_CLIENT_ID>`
4. Guardar el autorizador.
5. En **Routes**, asociar la ruta `ANY /{proxy+}` al autorizador `AzureAD-Authorizer` y apuntar la integracion HTTP hacia la IP o DNS del backend en EC2 en el puerto 8080.

---

## 3. Opcion 2: Federacion Mediante AWS Cognito User Pool

### 3.1 Crear la Aplicacion en Azure AD para Cognito
1. En Azure Portal > **Microsoft Entra ID** > **App registrations** > **New registration**:
   - Nombre: `RutaExpress-Cognito-Federation`
   - Tipos de cuenta: Inquilino unico.
2. Anotar el **Application (client) ID** y el **Directory (tenant) ID**.
3. Ir a **Certificates & secrets** > pestaña **Client secrets** > **New client secret**:
   - Copiar de inmediato el texto de la columna **Value** (este valor no se vuelve a mostrar).
4. Ir a **API permissions** y verificar la concesion de permisos para `openid`, `email` y `profile`.

### 3.2 Crear y Configurar el User Pool en AWS Cognito
1. En AWS Console, ir a **Cognito** > **Create user pool**.
2. Opciones de inicio de sesion: Seleccionar `Email` y marcar `Cognito user pool` y `Federated identity providers`.
3. Politicas de seguridad: Contrasena por defecto, MFA desactivado para desarrollo.
4. Envio de correo: `Send email with Cognito`.
5. Integracion de la aplicacion:
   - Nombre del User Pool: `rutaexpress-user-pool`
   - Dominio de Cognito: Asignar un prefijo unico (ejemplo: `rutaexpress-auth-<nombre>`).
   - Cliente de aplicacion inicial: Tipo `Public client`, nombre `rutaexpress-web-client`, sin generar client secret.
   - Callback URL: `http://localhost:5173/`
6. Finalizar la creacion y anotar:
   - User Pool ID (ejemplo: `us-east-1_xxxxxxxxx`).
   - App Client ID.
   - Dominio completo de Cognito (ejemplo: `https://rutaexpress-auth-<nombre>.auth.us-east-1.amazoncognito.com`).

### 3.3 Registrar la URL de Retorno en Azure AD
1. Volver a Azure AD > App `RutaExpress-Cognito-Federation` > **Authentication**.
2. Agregar plataforma **Web**.
3. En Redirect URIs, ingresar exactamente:
   ```text
   https://<DOMINIO_COMPLETO_COGNITO>/oauth2/idpresponse
   ```
4. Guardar cambios.

### 3.4 Conectar el Proveedor de Identidad OIDC en Cognito
1. En AWS Cognito, seleccionar el User Pool creado.
2. En el menu lateral, ir a **Authentication** > **Social and external providers**.
3. Hacer clic en **Add identity provider** y seleccionar **OpenID Connect (OIDC)**:
   - Provider name: `AzureAD`
   - Client ID: El Application ID de Azure.
   - Client secret: El secret copiado de Azure.
   - Authorize scope: `openid email profile`
   - Issuer URL: `https://login.microsoftonline.com/<AZURE_TENANT_ID>/v2.0`
4. Guardar el proveedor.
5. En el menu **Applications** > **App clients** > Seleccionar `rutaexpress-web-client`:
   - En la seccion de inicio de sesion, editar y marcar la casilla `AzureAD`.
   - Verificar los scopes `openid`, `email`, `profile`.
   - Guardar cambios.

---

## 4. Despliegue del Backend en Servidor AWS EC2

### 4.1 Configuracion de Grupos de Seguridad (Security Groups)
En la consola de EC2, crear un Security Group con las siguientes reglas de entrada:

| Protocolo | Puerto | Origen | Proposito |
|---|---|---|---|
| TCP | 22 | IP_Administrador/32 | Acceso por consola SSH |
| TCP | 8080 | 0.0.0.0/0 (o VPC de API Gateway) | Trafico HTTP hacia el Backend For Frontend (BFF) |

Los puertos 8081, 8082, 8083 y 5432 no deben abrirse al exterior; se comunican internamente en la maquina virtual mediante la red bridge de Docker.

### 4.2 Instalacion de Software en EC2 (Amazon Linux 2023)
Conectarse por SSH a la instancia y ejecutar:

```bash
# Actualizar el sistema
sudo dnf update -y

# Instalar Docker y Git
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

# Instalar Docker Compose v2
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Aplicar permisos al usuario
newgrp docker
```

### 4.3 Puesta en Marcha del Contenedor con Docker Compose

```bash
# Clonar el proyecto
git clone https://github.com/Separd-sudo/rutaexpress-backend.git
cd rutaexpress-backend

# Iniciar PostgreSQL y todos los microservicios
docker compose up -d --build

# Verificar el estado operativo
docker compose ps
```