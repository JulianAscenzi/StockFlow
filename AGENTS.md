# StockFlow agent instructions

## Project goal

StockFlow es un sistema web de inventario y ventas para pequeños comercios. El resultado debe poder ser usado por un comercio real sin necesitar una explicación técnica.

## Required context

Al comenzar cualquier tarea:

1. Leer `docs/ARCHITECTURE.md`.
2. Leer `docs/ROADMAP.md`.
3. Leer `docs/STATUS.md`.
4. Inspeccionar `git status` y los commits recientes.
5. Inspeccionar el código relacionado antes de proponer cambios.
6. No asumir que una tarea pendiente ya está implementada.
7. Tratar el código y los tests como fuente de verdad si la documentación quedó desactualizada.

## Technology

- Java 21.
- Spring Boot 4.1.1.
- PostgreSQL 17.
- Maven.
- Spring Data JPA y Hibernate.
- Flyway para todas las modificaciones de esquema.
- JUnit, Mockito, MockMvc y Testcontainers.
- Organización package-by-feature.
- BigDecimal para dinero.
- Instant para timestamps.
- Sin Lombok.
- Sin H2.

## Architecture

Respetar el flujo:

HTTP DTO → Controller → Service → Repository → PostgreSQL

Las entidades JPA no se devuelven directamente por HTTP. Los controllers solo coordinan HTTP, servicios y mappers. Las reglas de negocio pertenecen a servicios y entidades. Flyway controla el esquema; Hibernate solamente lo valida.

## Database rules

- Nunca modificar una migración Flyway ya aplicada.
- Crear una nueva migración para cada cambio de esquema.
- Usar nombres explícitos para constraints e índices.
- Mantener PostgreSQL como defensa final de integridad.
- No usar `ddl-auto=create` o `update`; usar `ddl-auto=validate`.
- Mantener `spring.jpa.open-in-view=false`.
- No agregar datos permanentes de prueba.
- Las pruebas deben usar PostgreSQL real mediante Testcontainers.

## Domain rules

- El dinero siempre usa BigDecimal, nunca double.
- El stock utiliza cantidades enteras.
- Product.stock no puede modificarse mediante un setter.
- Todo cambio de stock debe registrar StockMovement en la misma transacción.
- Los egresos de stock deben usar bloqueo pesimista.
- StockMovement es histórico e inmutable.
- Sale y SaleItem son históricos e inmutables después de confirmarse.
- Una venta debe conservar snapshots de nombre, SKU, precio y costo.
- No exponer colecciones JPA mutables.
- No agregar relaciones bidireccionales sin una necesidad demostrada.
- No implementar equals/hashCode/toString automáticamente en entidades.

## Development workflow

Trabajar de forma autónoma siguiendo `docs/ROADMAP.md` y `docs/STATUS.md`.

Para cada tarea:

1. Elegir la primera tarea pendiente y no bloqueada.
2. Mantener el cambio como una unidad funcional pequeña.
3. Inspeccionar código y migraciones relacionados.
4. Implementar producción y pruebas.
5. Ejecutar primero las pruebas específicas.
6. Ejecutar después `mvn test` completo.
7. Ejecutar `git diff --check`.
8. Revisar el diff completo buscando errores funcionales, problemas transaccionales, inconsistencias con migraciones, exposición de entidades, relaciones LAZY problemáticas, secretos, archivos generados y configuración local.
9. Si todo pasa, crear un commit convencional.
10. Actualizar `docs/STATUS.md` dentro del mismo commit o en el checkpoint correspondiente.
11. Continuar con la próxima tarea independiente si queda tiempo y no existe una decisión de producto bloqueante.

No pedir confirmación entre pasos rutinarios.

Detenerse y pedir intervención solamente si existe una decisión funcional con alternativas materialmente diferentes; se requieren credenciales o permisos nuevos; una operación sería destructiva; el working tree contiene cambios ajenos que interfieren; Docker, PostgreSQL o una dependencia obligatoria no están disponibles; o las pruebas no pasan y no puede determinarse la causa con seguridad.

## Git rules

- Confirmar que el working tree esté limpio antes de cada tarea.
- Un commit por unidad lógica y Conventional Commits.
- No usar `git add .`; agregar al staging únicamente rutas explícitas.
- Revisar `git diff --cached` antes del commit.
- No modificar, borrar o sobrescribir cambios del usuario.
- No hacer rebase, reset destructivo ni force push.
- No hacer push salvo que el usuario lo solicite explícitamente.
- No incluir secretos, `.env`, `target` ni archivos generados.

## Testing requirements

- Entidades y servicios: pruebas unitarias.
- Repositorios y transacciones: PostgreSQL/Testcontainers.
- Controllers: `@WebMvcTest` con dependencias mockeadas y advice real.
- Flujos completos: `@SpringBootTest` + MockMvc + PostgreSQL/Testcontainers.
- Concurrencia: coordinación determinista, Futures con timeout y sin Thread.sleep.
- Cada corrección de bug debe incluir una prueba de regresión.
- No duplicar pruebas sin aportar una garantía nueva.

Una tarea termina cuando compila, pasan sus pruebas específicas y la suite completa, `git diff --check` pasa, el diff fue revisado, la documentación de estado está actualizada, existe un commit limpio y el working tree queda limpio.

## Scope control

- No agregar funcionalidades futuras “por si acaso”.
- No introducir abstracciones para un solo uso sin beneficio claro.
- No agregar dependencias si el proyecto ya puede resolver el problema.
- No mezclar refactors con una funcionalidad.
- No continuar hacia frontend hasta completar y verificar el backend del MVP indicado en ROADMAP.
- No implementar cancelaciones, pagos, clientes, usuarios o facturación hasta que ROADMAP lo indique.
