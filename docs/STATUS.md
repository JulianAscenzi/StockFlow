# Status

## Completed modules

`category`, `product`, `inventory`, `common` y el agregado persistente `sale` están completados. El esquema incluye categorías, productos, movimientos de stock, ventas e ítems de venta.

## Current module

`sale`: falta el servicio transaccional que confirme ventas, valide que no estén vacías y coordine el inventario.

## Next task

Implementar `SaleService` con pruebas unitarias y transaccionales: debe prohibir ventas sin ítems y mantener el total consistente, sin crear todavía DTOs ni endpoints.

## Last general test result

Suite completa con PostgreSQL/Testcontainers: **283 pruebas, 0 fallos, 0 errores y 0 omitidas**.

## Pending decisions

Las métricas del dashboard, el alcance de autenticación y el frontend se definirán después del backend MVP. No bloquean la próxima tarea.

## Real blockers

No hay bloqueos reales conocidos.
