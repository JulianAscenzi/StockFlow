# Status

## Completed modules

El backend MVP está cerrado: `category`, `product`, `inventory`, `common`, `sale` y `dashboard` están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

MVP de portfolio terminado. `auth` conserva un único administrador inicial configurable por entorno, contraseñas BCrypt, JWT HS256 de ocho horas y una pantalla de inicio de sesión para instalaciones privadas. La demo gratuita de portfolio permanece pública con autenticación desactivada intencionalmente y datos ficticios.

## Deployment decision

El alcance aprobado es una demostración pública para CV: Vercel sirve la interfaz y Render Free la API con PostgreSQL. No se autorizan datos comerciales ni se contrata infraestructura de producción. La guía de despliegue explica la persistencia limitada, falta de backups y el smoke test de la demo.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **314 pruebas, 0 fallos, 0 errores y 0 omitidas**. La interfaz compila con Vite y la integración frontend–backend se verificó además en una base PostgreSQL temporal: categoría, producto, entrada, venta y resumen diario.

## Pending decisions

Ninguna para el MVP de portfolio. Un uso comercial futuro requerirá decidir proveedor, backups, operación y secretos propios antes de cargar información real.

## Real blockers

Ninguno dentro del alcance de portfolio.
