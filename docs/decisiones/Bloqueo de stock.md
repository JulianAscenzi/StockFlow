# Bloqueo de stock

Estado: implementado. Esta nota describe la solución observada en el código; no reconstruye una discusión histórica sobre alternativas.

## Problema

Si quedan tres unidades y dos ventas simultáneas solicitan dos cada una, sólo una puede confirmarse. Además, una venta con varios productos debe revertirse por completo si cualquiera de sus líneas no tiene stock suficiente.

## Solución actual

`ProductRepository.findByIdForUpdate` usa `PESSIMISTIC_WRITE`. Dentro de una transacción, `InventoryService` obtiene ese bloqueo antes de validar y modificar el stock, tanto para entradas como para salidas. Guarda el movimiento histórico en la misma transacción.

`SaleService.confirm` ordena los ítems por ID de producto antes de llamar a `InventoryService.decreaseStock`. Así dos ventas que reciben los mismos productos en orden inverso adquieren sus bloqueos en el mismo orden. Cada línea registra una salida con motivo `Sale`.

La confirmación de la venta, los descuentos y los movimientos comparten una transacción. Una excepción por stock insuficiente revierte también los cambios de las líneas procesadas previamente.

## Alternativas para entender la elección

- **Leer y guardar sin coordinación:** dos operaciones pueden validar el mismo stock disponible; esa lectura por sí sola no garantiza que ambas ventas sean válidas.
- **Control optimista mediante versión:** detectaría modificaciones concurrentes y exigiría definir cómo rechazar o reintentar la operación. No es la estrategia implementada para el stock.
- **Bloqueo pesimista:** coordina las modificaciones sobre cada producto mientras dura la transacción. Es la estrategia actual y está cubierta por pruebas con PostgreSQL.

## Consecuencias y límites

Las operaciones sobre el mismo producto pueden esperar a que termine otra transacción. Conviene mantener breve esa transacción. El orden por ID evita el ciclo de bloqueos entre las ventas con líneas invertidas contempladas por las pruebas; no constituye una garantía universal contra cualquier deadlock futuro.

Los snapshots de las líneas preservan la información histórica de la venta. Esa responsabilidad es distinta de coordinar los descuentos de stock; el modelo completo está en [Arquitectura](../ARCHITECTURE.md).

## Evidencia en el repositorio

- [ProductRepository](../../backend/src/main/java/com/julianas/stockflow/product/ProductRepository.java): consulta con bloqueo pesimista.
- [InventoryService](../../backend/src/main/java/com/julianas/stockflow/inventory/InventoryService.java): validación, descuento y movimiento transaccional.
- [SaleService](../../backend/src/main/java/com/julianas/stockflow/sale/SaleService.java): orden de productos y confirmación transaccional.
- [SaleServiceIntegrationTest](../../backend/src/test/java/com/julianas/stockflow/sale/SaleServiceIntegrationTest.java): `concurrentSalesCannotOversellStock`, `concurrentSalesWithOppositeLineOrdersCompleteWithoutDeadlocking` y `rollsBackTheSaleAndPreviousStockChangesWhenAnyItemHasInsufficientStock`.
- [InventoryServiceIntegrationTest](../../backend/src/test/java/com/julianas/stockflow/inventory/InventoryServiceIntegrationTest.java): `concurrentWithdrawalsAllowExactlyOneWinner`.

Los enlaces al código se consultan desde GitHub o el editor, ya que quedan fuera de la bóveda `docs`. La existencia de estas pruebas no implica que se hayan ejecutado al editar esta nota; el resultado registrado está en [Estado](../STATUS.md).

[Volver al inicio](../Inicio.md) · [Usar en la demostración](../portfolio/Guion%20de%20demostracion.md)
