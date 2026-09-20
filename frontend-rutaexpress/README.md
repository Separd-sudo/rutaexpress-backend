# Frontend - frontend-rutaexpress

Aplicacion web Single Page Application (SPA) desarrollada con React 18 y Vite para la plataforma logistica de ultima milla RutaExpress.

---

## 1. Funcion del Frontend

El frontend ofrece una interfaz de usuario interactiva y moderna para los distintos actores de la empresa. Se conecta unicamente con el Backend-For-Frontend (BFF en el puerto 8080) y no conoce la existencia interna de los microservicios individuales.

Vistas y operaciones segun el rol del usuario:
- Administrador (Admin): Visualiza indicadores globales (KPIs), volumen total de envios, estado de los vehiculos en catalogo y paquetes en transito.
- Despachador: Panel de control de bodega para recepcionar paquetes, cambiar estados operativos (Aceptado, En Bodega, En Ruta, Entregado) y registrar notas de operacion.
- Cliente: Formulario para registrar solicitudes de despacho ingresando peso, distancia y valor declarado; ademas de consultar el seguimiento de sus pedidos.
- Auditor: Linea de tiempo detallada que expone el historial inmutable de cada envio, indicando usuario, fecha, rol y direccion IP.

---

## 2. Variables de Configuracion (.env)

En la raiz de `frontend-rutaexpress/` se configura el archivo `.env`:

| Variable | Valor por Defecto | Descripcion |
|---|---|---|
| `VITE_BFF_URL` | `http://localhost:8080` | Direccion base de conexion hacia el microservicio BFF |
| `VITE_AZURE_TENANT_ID` | `common` | ID de Tenant en Microsoft Entra ID (Azure AD) |
| `VITE_AZURE_CLIENT_ID` | `00000000-...` | ID de aplicacion cliente registrado en Azure AD |
| `VITE_AZURE_API_CLIENT_ID` | `00000000-...` | ID de aplicacion API del backend |

---

## 3. Como Levantar el Frontend

### Requisitos previos:
- Node.js 18 o superior.
- npm 9 o superior.

### Pasos de ejecucion:
1. Instalar las dependencias del proyecto:
```bash
cd frontend-rutaexpress
npm install
```

2. Configurar el archivo de entorno:
Crear o verificar el archivo `.env` con el contenido:
```env
VITE_BFF_URL=http://localhost:8080
```

3. Iniciar el servidor local de desarrollo:
```bash
npm run dev
```

La aplicacion quedara accesible de inmediato en el navegador web en:
`http://localhost:5173`

### Inicio de Sesion en Desarrollo:
Al ingresar a `http://localhost:5173/login`, ademas del boton de inicio corporativo con Microsoft, se disponen de botones de acceso rapido por rol (Admin, Despachador, Cliente, Auditor) para evaluar las vistas sin necesidad de credenciales de Azure AD en pruebas locales.

---

## 4. Compilacion para Produccion

Para generar los archivos estaticos optimizados:
```bash
npm run build
```
Los archivos finales se generan en la carpeta `dist/`, listos para ser servidos por Nginx o subidos a un bucket de almacenamiento estatico en AWS S3.