# 🚀 Guía de Despliegue en AWS: RutaExpress (Para Estudiantes Duoc UC)

Esta guía explica paso a paso cómo conectar este repositorio con tu cuenta de **AWS Academy / Learner Lab** para levantar la arquitectura completa del proyecto **RutaExpress** (Evaluación EP3):

- **5 Microservicios Spring Boot** (BFF, Shipments, Catalog, Audit, RabbitMQ).
- **Clúster de RabbitMQ de 2 Nodos** sincronizados (`rabbitmq-1` y `rabbitmq-2`).
- **Base de Datos PostgreSQL** aislada en su propia máquina virtual.
- **AWS API Gateway (HTTP API)** protegido con JWT de Microsoft Entra ID (Azure AD), sin rutas inseguras `{proxy+}`.
- **Pipeline CI/CD automático** con GitHub Actions.

---

## 🏗️ Arquitectura de Servidores en AWS

Usaremos **2 instancias EC2** en la misma VPC (red de AWS):

```
                   [ Navegador / Frontend / Postman ]
                                  │
                                  ▼ (JWT Bearer Token de Azure)
                     [ AWS API Gateway (HTTP API) ]
                      Rutas explícitas sin {proxy+}
                                  │
                                  ▼ (Puerto 8080)
┌────────────────────────────────────────────────────────────────────────┐
│  EC2 N°1: "rutaexpress-apps" (Aplicaciones y Mensajería)              │
│                                                                        │
│   • ms-rutaexpress-bff       (:8080)  ──► Punto de entrada             │
│   • ms-rutaexpress-shipments (:8081)  ──► Publicador de eventos        │
│   • ms-rutaexpress-catalog   (:8082)                                   │
│   • ms-rutaexpress-audit     (:8083)                                   │
│   • ms-rutaexpress-rabbitmq  (:8084)  ──► RabbitAdminService & DLQ     │
│                                                                        │
│   • Clúster RabbitMQ (2 Nodos con Erlang Cookie):                      │
│       - rabbitmq-1 (AMQP :5672 | Web UI :15672)                        │
│       - rabbitmq-2 (AMQP :5673 | Web UI :15673)                        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Conexión JDBC interna (Puerto 5432)
                                    ▼ (Vía IP Privada 172.31.x.x)
┌────────────────────────────────────────────────────────────────────────┐
│  EC2 N°2: "rutaexpress-db" (Base de Datos Aislada)                     │
│                                                                        │
│   • Contenedor Docker: postgres:16-alpine                              │
│   • Volumen persistente: pgdata                                        │
│   • Bases de datos:                                                    │
│       - rutaexpress_catalog                                            │
│       - rutaexpress_audit                                              │
│       - rutaexpress_shipments                                          │
└────────────────────────────────────────────────────────────────────────┘
```

> **¿Por qué no usamos Amazon RDS administrado?**
> Las cuentas de AWS Academy Learner Lab tienen una política estricta (`Pvoclabs2`) que bloquea la creación de RDS (`rds:CreateDBInstance`). Correr PostgreSQL en una segunda EC2 dedicada cumple con el aislamiento de base de datos exigido y no tiene ningún costo extra en el laboratorio.

---

## PASO 1: Crear las 2 Instancias EC2 y el Security Group en AWS

### 1.1 Configurar el Security Group
1. En tu consola de AWS busca **EC2** $\rightarrow$ menú izquierdo **Security Groups** $\rightarrow$ **Create security group**.
2. **Security group name:** `rutaexpress-sg`
3. En **Inbound rules (Reglas de entrada)** agrega exactamente estas 6 reglas con origen `Anywhere-IPv4` (`0.0.0.0/0`):

| Type | Port range | Descripción |
| :--- | :--- | :--- |
| **SSH** | `22` | Para que GitHub Actions pueda desplegar por SSH |
| **Custom TCP** | `8080` | Puerto del **BFF** (comunicación con API Gateway) |
| **Custom TCP** | `8084` | Swagger y API REST de **RabbitMQ Service** |
| **Custom TCP** | `15672` | Consola Web de **RabbitMQ Nodo 1** |
| **Custom TCP** | `15673` | Consola Web de **RabbitMQ Nodo 2** |
| **PostgreSQL** | `5432` | Conexión interna hacia la Base de Datos |

4. Haz clic en **Create security group**.

---

### 1.2 Lanzar la EC2 N°1: `rutaexpress-apps`
1. Ve a **EC2 $\rightarrow$ Instances $\rightarrow$ Launch instances**.
2. **Name:** `rutaexpress-apps`
3. **AMI:** Amazon Linux 2023.
4. **Instance type:** `t3.small` o `t2.medium` (recomendado para soportar los 5 microservicios + el clúster de RabbitMQ).
5. **Key pair:** Selecciona tu archivo de llave (ej. `vockey` o tu llave `.pem`).
6. **Network settings:** Selecciona el Security Group `rutaexpress-sg`.
7. Haz clic en **Launch instance**.
8. **Anota su IP:**
   - **Public IPv4 address** (ej. `3.81.237.202`). La usaremos como `TARGET_HOST_IP`.

---

### 1.3 Lanzar la EC2 N°2: `rutaexpress-db`
1. Vuelve a **Launch instances**.
2. **Name:** `rutaexpress-db`
3. **AMI:** Amazon Linux 2023.
4. **Instance type:** `t3.micro` o `t2.micro` (es suficiente solo para la BD).
5. **Key pair:** La misma llave del paso anterior.
6. **Network settings:** El mismo Security Group `rutaexpress-sg`.
7. Haz clic en **Launch instance**.
8. **Anota sus 2 IPs:**
   - **Public IPv4 address** (para conectarte a configurarla).
   - **Private IPv4 address** (se ve como `172.31.x.x`). La usaremos como `RDS_HOST`.

---

## PASO 2: Levantar PostgreSQL en la EC2 de Base de Datos

Entra por SSH a tu máquina `rutaexpress-db` desde tu terminal (o usando el botón **Connect** de la consola de AWS):

```zsh
ssh -i /ruta/a/tu-llave.pem ec2-user@<IP_PUBLICA_EC2_DB>
```

Una vez adentro de la máquina, copia y pega estos comandos uno a uno:

```bash
# 1. Instalar y encender Docker
sudo dnf install -y docker
sudo systemctl enable --now docker

# 2. Levantar el contenedor de PostgreSQL 16 con almacenamiento persistente
sudo docker run -d --name rtx-postgres-db --restart unless-stopped -p 5432:5432 -e POSTGRES_PASSWORD=postgres -v pgdata:/var/lib/postgresql/data postgres:16-alpine

# 3. Crear las 3 bases de datos para los microservicios
sudo docker exec rtx-postgres-db psql -U postgres -c "CREATE DATABASE rutaexpress_catalog;"
sudo docker exec rtx-postgres-db psql -U postgres -c "CREATE DATABASE rutaexpress_audit;"
sudo docker exec rtx-postgres-db psql -U postgres -c "CREATE DATABASE rutaexpress_shipments;"

# 4. Salir de la máquina
exit
```

¡Listo! La base de datos ya está corriendo y lista para recibir conexiones en su IP privada.

---

## PASO 3: Configurar GitHub Secrets y Variables

Entra a tu repositorio en GitHub $\rightarrow$ **Settings $\rightarrow$ Secrets and variables $\rightarrow$ Actions**.

### 3.1 Pestaña "Secrets" (`New repository secret`):
- **`EC2_SSH_KEY`**: Abre tu archivo `.pem` con un editor de texto y copia todo su contenido (incluyendo las líneas `-----BEGIN RSA PRIVATE KEY-----` y `-----END RSA PRIVATE KEY-----`).
- **`RDS_PASSWORD`**: `postgres`

### 3.2 Pestaña "Variables" (`New repository variable`):
- **`TARGET_HOST_IP`**: La **IP pública** de tu máquina `rutaexpress-apps`.
- **`RDS_HOST`**: La **IP privada** (`172.31.x.x`) de tu máquina `rutaexpress-db`.
- **`RDS_PORT`**: `5432`
- **`RDS_USERNAME`**: `postgres`

---

## PASO 4: Desplegar Automáticamente con GitHub Actions

1. En tu repositorio de GitHub, entra a la carpeta `.github/workflows/deploy.yml`.
2. Asegúrate de que el archivo tenga la última versión del repositorio (puedes hacer un commit o pulsar **Run workflow** en la pestaña **Actions**).
3. GitHub Actions ejecutará automáticamente:
   - Compilación de los 5 microservicios con Maven y Java 17.
   - Creación y subida de las 5 imágenes Docker a GitHub Container Registry (`ghcr.io`).
   - Conexión por SSH a tu EC2 de aplicaciones (`TARGET_HOST_IP`).
   - Configuración de Swap de 2 GB para optimizar la memoria.
   - Descarga de imágenes y ejecución de `docker compose up -d`.
   - Inicialización del clúster RabbitMQ (`rabbitmq-1` y `rabbitmq-2`) y verificación de estado con `rabbitmqctl cluster_status`.

---

## PASO 5: Crear el API Gateway en AWS CloudFormation (Sin `{proxy+}`)

1. En la consola de AWS busca **CloudFormation** $\rightarrow$ **Create stack $\rightarrow$ With new resources (standard)**.
2. Selecciona **Upload a template file** $\rightarrow$ **Choose file** y sube el archivo de tu proyecto:
   `infra/aws/apigateway-rutaexpress.yml`
3. Haz clic en **Next** y completa los campos:
   - **Stack name:** `rutaexpress-apigateway`
   - **BffHost:** La **IP pública** de tu máquina `rutaexpress-apps`.
   - **BffPort:** `8080`
   - **StageName:** `dev`
   - **TenantId:** `b7bd70fc-34e6-4f0c-80cc-e0909f96d096`
   - **ApiClientId:** `1a9272d9-8271-48d5-a823-22b94af30424`
   - **RequiredScope:** `access_as_user`
   - **FrontendOrigins:** `http://localhost:5173,http://localhost:4200`
4. Haz clic en **Next $\rightarrow$ Next $\rightarrow$ Submit**.
5. Espera unos 20 segundos hasta que pase al estado **`CREATE_COMPLETE`**.
6. En la pestaña **Outputs** copia el valor de **`ApiBaseUrl`** (ej. `https://xxxx.execute-api.us-east-1.amazonaws.com/dev`). Esta es la URL que se configura en el Frontend.

---

## PASO 6: Demostración y Pruebas en Vivo (Rúbrica Nota 7.0)

Para demostrarle al profesor que todo funciona:

### 1. Panel Visual del Clúster RabbitMQ (2 Nodos)
- Abre en el navegador:
  - Nodo 1: `http://<IP_PUBLICA_EC2_APPS>:15672`
  - Nodo 2: `http://<IP_PUBLICA_EC2_APPS>:15673`
- Usuario: `rutaexpress` | Contraseña: `rutaexpress123`
- En la pestaña **Overview $\rightarrow$ Nodes** muestra los dos nodos activos en verde: `rabbit@rabbitmq-1` y `rabbit@rabbitmq-2`.
- En **Queues** muestra las colas activas con soporte DLQ (`q.cmd.warehouse`, `q.cmd.warehouse.dlq`, `q.cmd.label`, `q.cmd.label.dlq`).

### 2. Ejecutar Pruebas End-to-End desde tu computador hacia AWS
Abre una terminal en tu computador y ejecuta:

```zsh
BFF_URL="http://<IP_PUBLICA_EC2_APPS>:8080" RABBIT_URL="http://<IP_PUBLICA_EC2_APPS>:8084" ./test-e2e.sh
```

El script verificará automáticamente:
1. BFF en estado `UP`.
2. Microservicio RabbitMQ en estado `UP`.
3. Creación dinámica de colas, exchanges y bindings vía `RabbitAdminService`.
4. Validación `@Valid` de parámetros con rechazo `400 Bad Request`.
5. Envío de mensaje fallido y captura en la Dead Letter Queue (**DLQ**).
6. Tasa métrica de fallos y limpieza automática.

---

### 💡 Pregunta Típica del Profesor para Defender:
**"¿Por qué cada microservicio es una API y por qué el API Gateway apunta al BFF?"**
> *"Profesor, cada microservicio es un servicio REST independiente con su propia base de datos y puerto (`:8081`, `:8082`, `:8083`, `:8084`). Aplicando el patrón de arquitectura segura de las guías `1.3.4` y `1.3.5`, AWS API Gateway actúa como único punto de entrada público validando el token JWT de Azure Entra ID, protegiendo los microservicios internos detrás del BFF en el puerto `8080`, sin exponer directamente los puertos de dominio a Internet ni usar comodines `{proxy+}`."*
