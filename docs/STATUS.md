# Status

## Completed modules

`category`, `product`, `inventory`, `common` y `sale` hasta su confirmación persistente están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

`sale`: la confirmación valida ítems y total, bloquea productos en orden determinista, descuenta stock y registra movimientos `OUT` de forma atómica. Falta verificar la sobreventa en ventas concurrentes.

## Next task

Impedir sobreventa cuando se confirman ventas concurrentes, sin crear todavía DTOs ni endpoints.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **291 pruebas, 0 fallos, 0 errores y 0 omitidas**.

## Pending decisions

Las métricas del dashboard, el alcance de autenticación y el frontend se definirán después del backend MVP. No bloquean la próxima tarea.

## Real blockers

No hay bloqueos reales conocidos.
