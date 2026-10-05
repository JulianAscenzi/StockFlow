# Operación del backend

Esta guía cubre las capacidades operativas del backend y su validación local. La ejecución Kubernetes local complementaria está en [KUBERNETES](KUBERNETES.md); no se agrega cloud. La observabilidad local está documentada en [OBSERVABILITY](OBSERVABILITY.md). El arranque y desarrollo están en [LOCAL_DEVELOPMENT](LOCAL_DEVELOPMENT.md).

## Health y tráfico

Fuera del perfil `observability` sólo se expone Actuator `health`, sin componentes ni detalles incluso con un JWT válido. Seguridad permite GET anónimo exactamente sobre `/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness`. No se habilitan env, metrics ni otros subpaths. En Compose, health y prometheus se sirven en management 9091; ver la política exacta en OBSERVABILITY.

| Endpoint | Incluye | Interpretación operativa |
| --- | --- | --- |
| `/actuator/health/liveness` | `livenessState` de Spring Boot | Un fallo persistente puede justificar reiniciar la instancia |
| `/actuator/health/readiness` | `readinessState` y `db` | Un fallo retira la instancia del tráfico; no exige reiniciarla |
| `/actuator/health` | Health general de Boot | Compatibilidad con Render y healthcheck de Compose |

La caída de PostgreSQL no significa que el proceso JVM esté roto. Por eso no forma parte de liveness. Readiness sí verifica la DB porque las operaciones comerciales la necesitan. Un proceso vivo también puede no estar listo durante bootstrap o apagado. HTTP 200 indica UP; DOWN/OUT_OF_SERVICE devuelve 503. Estas probes no garantizan detectar todos los deadlocks ni reemplazan la startup probe y política de umbrales del [laboratorio Kubernetes](KUBERNETES.md#probes-y-arranque).

Compose conserva el healthcheck general y no reinicia automáticamente un contenedor por quedar unhealthy. `depends_on` ordena el arranque inicial; no implementa retirada de tráfico durante una caída posterior. Las respuestas 503 siguen siendo necesarias en Compose; Kubernetes sí retira endpoints mediante readiness.

```bash
docker compose exec backend curl -i http://backend-management:9091/actuator/health/liveness
docker compose exec backend curl -i http://backend-management:9091/actuator/health/readiness
```

## Bootstrap de administrador

Antes de consultar si hay usuarios, `AuthService` adquiere `pg_advisory_xact_lock(1937006967, 1)` dentro de una transacción READ_COMMITTED. Es una clave fija compartida entre nodos; usa el espacio de dos enteros, separado del lock bigint de idempotencia de ventas. El segundo nodo espera hasta el commit o rollback del primero y entonces consulta el estado confirmado. Si ya hay una cuenta, no crea ni cambia otra, aunque sus credenciales de bootstrap sean diferentes.

La carrera anterior permitía que ambos nodos observaran cero usuarios: con el mismo email, uno fallaba por unicidad; con emails distintos, podían crear dos cuentas. El lock coordina toda la decisión y la inserción. PostgreSQL conserva la constraint de email único como defensa adicional. No hay locks en memoria ni servicios externos; no se capturan errores inesperados como si fueran éxitos. Si la creación falla, la transacción revierte y libera el lock. La disponibilidad pasa a aceptar tráfico después de finalizar los runners.

Las variables APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD sólo inicializan una base vacía, no rotan una contraseña existente. No se registra email, contraseña ni hash en los eventos de bootstrap.

## SIGTERM y graceful shutdown

Spring Boot 4.1.1 ya habilita graceful shutdown por defecto. Se conserva ese soporte, sin sleeps ni hooks propios, y se explicita `spring.lifecycle.timeout-per-shutdown-phase=30s`. Al cerrar el contexto, Boot cambia readiness a REFUSING_TRAFFIC; Tomcat deja de aceptar conexiones nuevas y permite finalizar requests activas durante la fase de gracia. Luego se cierran JPA y HikariCP.

Compose usa `stop_grace_period: 40s`, superior a la fase HTTP de 30s, para que su plazo por defecto de 10s no corte el cierre. Es un presupuesto local, no una garantía de que cualquier cierre de múltiples fases termine en 40s. Un futuro orquestador debe contemplar además propagación de retirada de tráfico y fases de cierre. Al agotar el plazo se pueden interrumpir requests; no se garantiza éxito de escrituras cortadas ni rollback de una operación que ya confirmó. Las ventas conservan su recuperación idempotente.

Validación reproducible en una base exclusivamente de prueba: mantener un lock de fila con una sesión psql abierta, iniciar una entrada de stock por HTTP, observar su espera en pg_stat_activity, enviar SIGTERM al contenedor API y liberar el lock. La request debe terminar, seguido de los mensajes de graceful shutdown y cierre de Hikari. No se agrega una ruta lenta ni pg_sleep a la aplicación.

La validación real terminó con HTTP 200 para la entrada en curso, stock y movimiento confirmados, graceful shutdown completo y cierre de Hikari. Java salió con 143 (128 + SIGTERM), compatible con terminación por esa señal; no hubo SIGKILL. No se interpreta ese código por sí solo como un fallo de cierre.

## Request ID y logs

`RequestIdFilter` se ejecuta antes de seguridad. Acepta un único header `X-Request-ID` de entre 1 y 64 caracteres: primero alfanumérico, resto alfanumérico, punto, guion o guion bajo. Si falta, es inválido o se repite, genera un UUID. No es un identificador de seguridad ni garantiza unicidad si lo propone el cliente.

El ID se conserva en un atributo de la request, se devuelve en `X-Request-ID` y se añade al MDC como `requestId`. También está presente en rechazos de seguridad y dispatches servlet async/error. Cada dispatch restaura el contexto anterior al terminar, incluso frente a excepciones. No se propaga automáticamente a ejecutores ni tareas de background: si se incorporan, requerirán propagación explícita. Los endpoints actuales son síncronos.

CORS permite enviar y leer ese header desde los orígenes ya autorizados. No abre nuevos orígenes. El formato local legible añade `[requestId=...]`; fuera de una request muestra `none`. El perfil `prod` activa el JSON Logstash incorporado en Boot, con los campos de MDC, sin nuevas dependencias. Los eventos de inicio, errores y bootstrap son suficientes para esta etapa; no se agrega logging de cada método ni payloads.

No habilitar logging de binds SQL, requests completas o headers de autorización en un entorno con datos sensibles. El código nuevo no registra passwords, JWT, email, claves de idempotencia ni cuerpo de requests. Los errores internos conservan sus excepciones y stack traces para diagnóstico; las respuestas HTTP son sanitizadas.

## Timeouts y límites

| Área | Estado previo/default | Decisión de esta etapa |
| --- | --- | --- |
| Hikari: espera de conexión | 30.000 ms | 3.000 ms, para responder a falta de conexiones antes del healthcheck HTTP de Compose de 4s |
| Hikari: validación de conexión | 5.000 ms | 1.000 ms, menor que la adquisición y dentro del presupuesto de probe |
| Pool Hikari | Máximo 10 por instancia; minimumIdle hereda máximo | Sin dimensionamiento nuevo; las réplicas multiplicarán conexiones |
| pgJDBC connectTimeout | 10s | Sin cambiar; crear conexiones físicas y esperar por el pool son plazos diferentes |
| pgJDBC socketTimeout/queryTimeout | 0, sin límite | Sin cambiar; falta una política medida para red silenciosa/consultas largas |
| PostgreSQL lock_timeout/statement_timeout/transaction_timeout | 0, deshabilitados | Sin cambiar; no se impone un límite nuevo a locks de stock ni a migraciones |
| Transacciones Spring/JPA | Sin timeout explícito; heredan el sistema transaccional | Sin cambiar; no existe un deadline universal de request |
| HTTP Tomcat | connectionTimeout 60s del conector embebido; keepAliveTimeout hereda ese valor, no es deadline de servicio | Se conservan defaults; no se usa connection-timeout para limitar lógica comercial |
| Spring MVC async | Hereda servidor si no se configura | Sin cambiar; API síncrona |
| Cierre de Boot | Graceful y fase de 30s | Fase explícita y margen Docker de 40s |

Los 3s/1s son un presupuesto de operación local alineado con Compose, no un SLO comercial ni un límite de duración de una query. Se pueden sobrescribir con `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` y `SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT` en el entorno real del backend, manteniendo validación menor que adquisición. Si se amplía el plazo de adquisición, también hay que ampliar el presupuesto del cliente de healthcheck. No habilitamos `lock_timeout` sin carga representativa y contrato de cancelación; por eso las pruebas concurrentes mantienen su semántica original.

Una conexión ya prestada que deja de recibir paquetes puede bloquear una lectura más allá de los 3s. Este presupuesto no cubre un blackhole de red, una query interminable ni una transacción larga. Eso queda explícitamente pendiente junto con deadlines JDBC/transaccionales, pruebas de cancelación y calibración por carga. No usar la configuración de este laboratorio como garantía contra cualquier fallo de red.

## PostgreSQL no disponible

El advice distingue fallos de recursos DB y SQLStates de conexión (`08...`) o apagado/no disponible (`57P01`, `57P02`, `57P03`). Devuelve 503 `DATABASE_UNAVAILABLE`, mensaje genérico y request ID; no expone SQL, stack traces ni conexión. Los conflictos de integridad siguen siendo 409. Un error de transacción o SQL de programación sin evidencia de caída sigue siendo 500, con diagnóstico interno.

No se reintentan escrituras automáticamente: una desconexión puede ocurrir después del commit. El cliente debe conservar la clave de idempotencia para recuperar una venta, como antes.

Experimento seguro: crear un proyecto Compose separado con un archivo de entorno temporal, ejecutar un flujo comercial, consultar ambas probes, detener sólo su servicio database, repetir probes y dashboard autenticado, iniciar database y esperar readiness UP. Nunca ejecutar el experimento sobre la base de uso diario.

Resultados verificados el 2026-10-03:

| Momento | Liveness | Readiness | Dashboard autenticado |
| --- | --- | --- | --- |
| Base disponible | 200 UP | 200 UP | 200 |
| Base detenida | 200 UP (~0,09s) | 503 DOWN (~3,10s) | 503 DATABASE_UNAVAILABLE (~3,02s) |
| Base restaurada | 200 UP | 200 UP | 200, venta y stock conservados |

La API recuperó conexiones sin cambiar su StartedAt ni RestartCount. Se validó una detención explícita de PostgreSQL, no una pérdida silenciosa de paquetes.

## Perfiles y precedencia

`application.properties` conserva configuración común para Maven, Compose y Render. `application-prod.properties` sólo añade JSON y desactiva el banner. Activar con `SPRING_PROFILES_ACTIVE=prod`; Compose pasa esa variable si se define en `.env`. Sin perfil, los logs son legibles. Render puede configurarla en su entorno sin modificar el Blueprint.

Maven Surefire activa `test`, cuyo archivo está sólo en src/test/resources y contiene credenciales ficticias/overrides de tests. Antes, un application.properties de test ocultaba la configuración principal; ahora las pruebas heredan realmente probes y Hikari. Los overrides específicos de cada prueba y DynamicPropertySource siguen proporcionando PostgreSQL temporal. Ese perfil no se empaqueta en la imagen.

En ejecución habitual, argumentos CLI y variables de entorno pueden sobrescribir los archivos empaquetados; el archivo del perfil sobrescribe el común. `.env` no lo lee Spring automáticamente: Compose sólo pasa lo declarado y Maven local requiere exportar variables. El postprocessor existente de Render añade DATABASE_URL con prioridad sobre las propiedades de datasource; permanece sin cambios. No usar secretos dentro de VITE_*.

## Fuentes

- [Actuator: probes y health groups](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).
- [Graceful shutdown de Spring Boot](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html).
- [Logging estructurado y MDC](https://docs.spring.io/spring-boot/reference/features/logging.html).
- [Configuración HikariCP](https://github.com/brettwooldridge/HikariCP).
- [Parámetros pgJDBC](https://jdbc.postgresql.org/documentation/use/).
- [Conector HTTP de Tomcat 11](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html).
- [Timeouts PostgreSQL 17](https://www.postgresql.org/docs/17/runtime-config-client.html).

## Objetivos y alertas locales

La Etapa 4 está documentada en [SRE](SRE.md). Para interpretar burn rate, readiness y scraping sin confundir fallos de negocio con dependencia técnica, seguir [RUNBOOK](RUNBOOK.md). Alertas se definen en Prometheus, no mediante clicks en Grafana; Alertmanager recibe sólo localmente.

## Operación en Kubernetes

[KUBERNETES](KUBERNETES.md) agrega probes efectivas, startup budget, dos réplicas, recuperación del controlador, PVCs y despliegues/rollback. Readiness retira endpoints cuando PostgreSQL cae; liveness conserva las JVM. Boot mantiene su fase de 30s y Kubernetes concede 45s. El PDB protege una réplica frente a eviction voluntaria, no delete pod ni fallos físicos. Scripts fijan el contexto del laboratorio y nunca usan volúmenes Compose.
