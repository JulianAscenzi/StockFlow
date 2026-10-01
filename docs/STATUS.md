# Status

## Completed modules

El backend MVP está cerrado: `category`, `product`, `inventory`, `common`, `sale` y `dashboard` están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

MVP de portfolio terminado. `auth` conserva un único administrador inicial configurable por entorno, contraseñas BCrypt, JWT HS256 de ocho horas y una pantalla de inicio de sesión para instalaciones privadas. La demo gratuita de portfolio permanece pública con autenticación desactivada intencionalmente y datos ficticios; en ese modo no se crean los componentes ni la ruta JWT y no se requiere `APP_JWT_SECRET`. El catálogo de productos se pagina en la interfaz y los selectores de categorías, ventas e inventario cargan todas las páginas para superar el límite de 100 registros. La confirmación de venta bloquea sus controles mientras la solicitud está en curso.

## Deployment decision

El alcance aprobado es una demostración pública para CV: Vercel sirve la interfaz y Render Free la API con PostgreSQL. No se autorizan datos comerciales ni se contrata infraestructura de producción. La guía de despliegue explica la persistencia limitada, falta de backups y el smoke test de la demo.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **315 pruebas, 0 fallos, 0 errores y 0 omitidas**. La interfaz compila con Vite y la integración frontend–backend se verificó además en una base PostgreSQL temporal: categoría, producto, entrada, venta y resumen diario.

## Cuaderno del proyecto

La carpeta `docs` puede abrirse como bóveda de Obsidian desde [Inicio](Inicio.md). Incluye una decisión sobre bloqueo de stock y un guion de demostración, con enlaces Markdown y configuración personal excluida de Git. El cambio documental se valida mediante revisión de enlaces locales y `git diff --check`; por autorización explícita del usuario no se ejecuta Maven para este cambio, dado que Docker no está disponible. El resultado general anterior corresponde a la última ejecución registrada, no a esta actualización documental.

## Pending decisions

Ninguna para el MVP de portfolio. Un uso comercial futuro requerirá decidir proveedor, backups, operación y secretos propios antes de cargar información real.

## Real blockers

Ninguno dentro del alcance de portfolio.

## Ampliaciones en curso

Docker es accesible con ejecución fuera del sandbox. La verificación inicial detectó una regresión previa: el advice convertía rutas inexistentes en 500; se corrige a 404 `RESOURCE_NOT_FOUND`, con regresión para login deshabilitado.

Verificación del 2026-10-01: regresión específica y suite backend completa aprobadas (315 pruebas).

Bloque 24 completado: Playwright aislado, cinco escenarios aprobados, compilación frontend aprobada y 315 pruebas backend aprobadas. El resumen vuelve a consultar al navegar y descarta respuestas obsoletas. Próximo bloque: CI.

Bloque 25: workflow CI con jobs backend, frontend y navegador dependiente de ambos. Ubuntu 24.04, Java 21, Node 24, Docker obligatorio, comprobación de pruebas ejecutadas sin omisiones e informes por siete días. La primera ejecución remota queda pendiente de que el usuario publique la rama; no se hizo push.
