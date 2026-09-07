# Status

## Completed modules

El backend MVP está cerrado: `category`, `product`, `inventory`, `common`, `sale` y `dashboard` están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

`auth`: existe un único administrador inicial configurable por entorno, contraseñas BCrypt, JWT HS256 de ocho horas y una pantalla de inicio de sesión. La demo gratuita de portfolio permanece pública con autenticación desactivada intencionalmente y datos ficticios.

## Next task

Preparar el despliegue de producción: elegir infraestructura persistente con backups y cargar secretos de administrador/JWT. La demo pública actual sólo sirve como muestra de portfolio.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **314 pruebas, 0 fallos, 0 errores y 0 omitidas**. La interfaz compila con Vite y la integración frontend–backend se verificó además en una base PostgreSQL temporal: categoría, producto, entrada, venta y resumen diario.

## Pending decisions

Queda elegir proveedor, política de backups y operación para datos comerciales. La demo de portfolio no debe contener datos reales.

## Real blockers

No hay bloqueos técnicos para la demo pública. El avance hacia uso comercial requiere infraestructura con persistencia, respaldos y secretos configurados por el operador.
