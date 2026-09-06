# Despliegue de piloto privado

Esta guía prepara un piloto privado en Vercel (interfaz), Render (API y PostgreSQL) y Cloudflare Access (control temporal de acceso). No reemplaza la autenticación propia de la aplicación: no se debe abrir a público ni usar con personal no autorizado hasta implementar el bloque de autenticación del roadmap.

## Límites de seguridad

- No se suben contraseñas ni URLs de conexión al repositorio. Render crea `DATABASE_URL` desde la base asociada.
- La API acepta CORS únicamente desde `APP_CORS_ALLOWED_ORIGINS`. CORS no es autenticación: bloquea navegadores ajenos, no solicitudes directas.
- Protegé tanto `app.tudominio.com` como `api.tudominio.com` con Cloudflare Access. La protección de la interfaz por sí sola no protege la API.
- Antes de cargar datos reales, configurá y probá recuperación de PostgreSQL. No se considera listo un despliegue sin una restauración de prueba.

## 1. Dominio y Cloudflare

1. Registrá un dominio y agregalo a Cloudflare. Usaremos `app.tudominio.com` para la interfaz y `api.tudominio.com` para la API.
2. En Cloudflare Zero Trust, creá una aplicación **Self-hosted** para cada hostname.
3. Creá una política **Allow** limitada inicialmente a los correos de las personas autorizadas. Usá un proveedor de identidad o códigos de un solo uso por correo.
4. No habilites los DNS definitivos todavía: primero necesitás las URLs de Vercel y Render.

Cloudflare Access deniega por defecto; verificá con una ventana privada que un correo no autorizado no pueda entrar a ambos hostnames.

## 2. Base y API en Render

1. Conectá este repositorio a Render y creá un Blueprint desde `render.yaml`.
2. Conservá PostgreSQL 17 y la región `virginia` que declara el archivo, salvo que se acuerde otra región antes de crear la base. La elección no se puede cambiar después.
3. Elegí los planes indicados como mínimo inicial y revisá el costo mostrado por Render antes de confirmar. No uses una base efímera o sin recuperación para datos comerciales.
4. Al crear el Blueprint, Render pedirá `APP_CORS_ALLOWED_ORIGINS`. Cargá exactamente `https://app.tudominio.com`, sin barra final. Si se usa temporalmente el dominio de Vercel, agregalo separado por coma.
5. Dejá `autoDeploy` desactivado hasta aprobar el smoke test. Render obtiene `DATABASE_URL` internamente; la aplicación lo adapta al formato JDBC y Flyway aplica/valida el esquema al arrancar.
6. Tras el primer despliegue, comprobá `https://<url-de-render>/actuator/health`: debe responder `200` y no mostrar detalles internos.
7. Añadí `api.tudominio.com` como dominio personalizado en Render y configurá en Cloudflare el DNS que Render indique. Aplicá Access a ese hostname.

La base no requiere ni debe exponer un puerto público. El Blueprint incluye una lista de IP pública vacía para que no se creen reglas de acceso externo.

## 3. Interfaz en Vercel

1. Importá el mismo repositorio en Vercel.
2. Configurá **Root Directory** como `frontend`; Vercel detectará Vite y ejecutará `npm ci`/`npm run build`.
3. En las variables de entorno de producción, definí `VITE_API_BASE_URL=https://api.tudominio.com`. Es una URL pública, no un secreto. Nunca uses una variable `VITE_*` para contraseñas.
4. Creá un preview y probá la interfaz. Sólo después promovelo a producción.
5. Asociá `app.tudominio.com` en Vercel, aplicá el DNS indicado por Vercel en Cloudflare y protegelo con su aplicación de Access.

## 4. Backups y comprobación de recuperación

1. Habilitá las copias y la recuperación puntual disponibles en el plan de PostgreSQL elegido.
2. Exportá una copia lógica inicial y guardala en un lugar cifrado con acceso limitado.
3. Una vez por trimestre, restaurá una copia en una base temporal y verificá categorías, productos, una venta y sus movimientos. Eliminá esa base temporal al terminar.

## Smoke test de aceptación

Con una cuenta autorizada y sobre el dominio final:

1. Abrí `app.tudominio.com`; un visitante no autorizado debe ser rechazado.
2. Creá una categoría y un producto.
3. Registrá una entrada de stock, confirmá una venta y comprobá el resumen diario.
4. Confirmá que la venta redujo el stock y dejó su movimiento histórico.
5. Intentá abrir directamente `api.tudominio.com/api/dashboard` sin autorización: Access debe rechazarlo.
6. Revisá logs de Render y Vercel: no deben contener contraseñas ni errores.

No cargues operación real hasta completar cada punto y guardar fecha, responsable y resultado del test.
