# frontend-rutaexpress

Aplicacion web Single Page Application (SPA) desarrollada con React 18 y Vite para la plataforma de envios de ultima milla RutaExpress. El frontend se comunica exclusivamente con el Backend-For-Frontend (`ms-rutaexpress-bff`), abstrayendo los microservicios de dominio.

---

## 1. Responsabilidades Funcionales

- Autenticacion corporativa basada en Microsoft Entra ID (Azure AD) mediante `@azure/msal-react` y `@azure/msal-browser`.
- Inyeccion automatica del token Bearer JWT en todas las solicitudes salientes hacia el BFF mediante interceptores de Axios.
- Enrutamiento protegido por perfiles (`Admin`, `Despachador`, `Cliente`, `Auditor`).
- Pantalla de inicio de sesion corporativo (`/login`) con boton oficial de Microsoft y soporte de evaluacion local.
- Panel de control (`/dashboard`) adaptado al rol del usuario autenticado:
  - **Admin**: KPIs operativos de red (total de envios, capacidad de flota en catalogo, envios en ruta).
  - **Despachador**: Gestion de envios en bodega y en ruta, cambio secuencial de estados de despacho.
  - **Cliente**: Listado de solicitudes propias y creacion de nuevos envios.
  - **Auditor**: Trazabilidad y timeline cronologico de eventos de auditoria inmutable.

---

## 2. Variables de Entorno

| Variable | Valor por Defecto | Descripcion |
|---|---|---|
| `VITE_BFF_URL` | `http://localhost:8080` | URL base del microservicio BFF |
| `VITE_AZURE_TENANT_ID` | `common` | Identificador del Tenant de Azure Active Directory |
| `VITE_AZURE_CLIENT_ID` | `00000000-...` | Application (Client) ID del registro frontend en Azure AD |
| `VITE_AZURE_API_CLIENT_ID` | `00000000-...` | Application (Client) ID del recurso backend (BFF) |

---

## 3. Instalacion y Ejecucion Local

Requisitos:
- Node.js 18 o superior
- npm 9 o superior

Instalacion de dependencias:
```bash
cd frontend-rutaexpress
npm install
```

Ejecucion en servidor de desarrollo:
```bash
npm run dev
```
La aplicacion estara disponible en `http://localhost:5173`.

Compilacion para produccion:
```bash
npm run build
```

---

## 4. Construccion de Contenedor Docker

```bash
docker build -t rutaexpress-frontend:1.0.0 .
docker run -d -p 80:80 rutaexpress-frontend:1.0.0
```