# StockFlow — diseño AWS y Terraform validado, sin provisionar

El [ADR 0001](../docs/adr/0001-cloud-runtime.md) elige ECS Fargate + ALB + RDS para ampliar la demostración AWS sin repetir Kubernetes. [AWS_COSTS](../docs/AWS_COSTS.md) estima USD 61,74/mes para lab y USD 240,33/mes para un ejemplo productivo distinto. **Esta etapa no autoriza plan contra AWS, apply, destroy ni comandos AWS sobre la cuenta.** Los comandos que pueden consultar/modificar AWS abajo son instrucciones para una etapa futura con autorización explícita.

El diseño y la implementación IaC forman parte del portfolio completado. Se mantienen sin provisionamiento permanente para evitar costos cloud innecesarios. Siete tests con provider mock y validación estática no prueban operación AWS; el reporte de preparación [9A](../docs/TERRAFORM_PLAN_REVIEW.md) conserva los límites de un plan real que no se ejecutó. El cierre actual no requiere continuar esa etapa.

## Alcance y estructura

Flat root, sin módulos externos:

| Archivo | Responsabilidad |
|---|---|
| `versions.tf`, `providers.tf`, `.terraform.lock.hcl` | Terraform ~> 1.16.0, AWS ~> 6.0, checksums/version exacta resuelta |
| `variables.tf`, `locals.tf`, `terraform.tfvars.example` | Entradas no secretas, validaciones, nombres y tags |
| `network.tf`, `security.tf` | Dos AZ, public subnets ALB/tasks y subnets DB aisladas, reglas SG |
| `database.tf` | RDS 17 cifrado, gp3, backups, master secret administrado, protección y snapshot |
| `ecs.tf`, `load-balancer.tf`, `iam.tf` | Imagen digest, task/service, probes, ALB, execution role |
| `observability.tf`, `cost-controls.tf`, `outputs.tf` | Logs con retención, budget opt-in, identificadores sin secretos |
| `tests/safety.tftest.hcl` | Tests estructurales con provider AWS mock; nunca configuran AWS real |

Sin recursos EKS, NAT, certificados, Route 53, state S3, registry nuevo ni CD. No cambia Docker/Compose/Helm, backend, frontend ni observabilidad local.

## Red, compute y salud

VPC `/16` privado, pública `10.42.0.0/24` y `10.42.1.0/24`, DB `10.42.10.0/24` y `10.42.11.0/24`. Públicas con ruta IGW; DB sólo ruta local. Tasks públicas por descarga GHCR, SG sin ingreso directo Internet. HTTPS outbound 443 abierto porque GHCR/CDN y AWS APIs tienen destinos variables. SG no filtra consultas al resolver DNS AmazonProvidedDNS. Las conexiones de respuesta se permiten por carácter stateful de SG. No egress DB iniciado ni ruta pública DB.

ALB escucha HTTP 80, **ingress vacío por defecto**. Una lista explícita de CIDRs de operadores permite pruebas técnicas; se rechaza `0.0.0.0/0`. No enviar admin password/JWT por HTTP ni usar este listener como producción. Target 8080, health check readiness 9091 (incluye DB), listener bloquea `/actuator` y `/actuator/*`. Management no tiene listener público; SG 9091 sólo ALB. ALB fail-open cuando todos los targets fallan es una limitación de balanceo, no seguridad de API.

Fargate Linux/amd64, 512 CPU units/1024 MiB, deseado 1; válido frente a límites del laboratorio Kubernetes, pero requiere medición de memoria/CPU en ECS. Permite pares CPU/memoria pequeños válidos, replicas 0/1/2. Rolling 100%/200%, circuit breaker rollback y grace 180s; puede duplicar costo task temporalmente. Contenedor comprueba liveness con curl incluido en imagen. Stop timeout/deregistration 60s, fase Spring 30s. Una primera implantación fallida no tiene deployment anterior al que volver.

No root (`stockflow` ya existe en imagen), sin capabilities, sin privileged ni ECS Exec. Root filesystem escribible porque la imagen necesita `/tmp`: ECS no reproduce `fsGroup`/`emptyDir` automáticamente. Endurecerlo con un volumen temporal escribible por el usuario requiere validación antes del despliegue; no se altera la imagen especulativamente. Fargate no ofrece todas las opciones de securityContext Kubernetes.

## Requisitos y validación local sin AWS

Terraform estable 1.16.x, acceso de red a registry/release oficiales para descargar provider, sin cuenta ni credenciales. Se validó con 1.16.5 y AWS 6.67.0. Mantener lockfile versionable; `init -upgrade` sólo tras revisar actualización. Ejecutar desde este directorio:

```bash
terraform fmt -check -recursive
terraform init -backend=false -input=false
terraform validate
terraform test
```

`init` descarga plugins/checksums, no provisiona AWS. `validate` verifica configuración/esquema, no existencia de secrets, cuotas, AZ, imagen ni orderability de RDS. No ejecutar `terraform plan` en esta etapa: un plan normal autentica y consulta AWS aunque no cree recursos. No se usan credentials falsas ni switches que oculten validaciones del provider. `terraform test` utiliza sólo `mock_provider "aws"` y `command=plan`: planes estructurales en memoria, sin AWS ni apply. Comprueba defaults de seguridad, overrides, combinaciones Fargate y rechazos de inputs peligrosos; no demuestra capacidad/permisos reales. No añadir providers reales a esos tests mientras rija la restricción de esta etapa.

## Entradas y secretos

Copiar `terraform.tfvars.example` a un archivo local ignorado **sólo al preparar un despliegue autorizado**. El ejemplo contiene digest cero y ARNs ficticios: **no es desplegable**. No son secretos ni evidencias de una release real. Entradas: región/dos AZ, project/environment, CIDR, GHCR repository/digest, CPU/memoria/replicas, clase/storage/nombre DB, Multi-AZ, backups/deletion protection/snapshot, retención logs, tags, ARNs secrets/KMS y budget opt-in. No password/token variables. Cambiar región implica cambiar AZs y reestimar costos.

`backend_image_repository@backend_image_digest` apunta al OCI ya validado GHCR, sin reconstruir imagen ni latest. Antes del deployment confirmar que el manifest contiene linux/amd64 y attestations asociadas. El nombre del repo es configurable; digest obligatorio independiente. Kind local sigue disponible sin AWS.

Secrets Manager se elige por integración RDS y claves JSON ECS; [SSM Parameter Store Standard](https://aws.amazon.com/systems-manager/pricing/) reduce costo, pero master administrado y rotación quedarían como otro flujo. Terraform **no crea versiones, no genera ni lee valores**:

* RDS `manage_master_user_password=true`: AWS genera y gestiona contraseña master en un apply futuro. State conserva metadata/ARN, sin password administrado por Terraform. El backend no usa ese usuario.
* `db_application_secret_arn`: externo, claves JSON `username`, `password` del usuario DB propio. Permisos de conexión y ownership/DDL del esquema StockFlow para Flyway, sin superuser ni `rds_superuser`; limitar a esta DB. Revisar la inicialización del esquema/migraciones con ese rol antes de ejecutar ECS.
* `application_secret_arn`: externo, claves `adminEmail`, `adminPassword`, `jwtSecret`; cumplir los requisitos existentes de autenticación de StockFlow (secret JWT suficientemente largo y aleatorio). No poner valores en tfvars/CLI/history/release outputs.

ARN no es secreto. ECS JSON key injection usa plataforma 1.4.0 y execution role GetSecretValue. [Rotar secret no actualiza una task existente](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html); redeploy explícito tras rotación. Admin bootstrap tampoco cambia automáticamente password de admin ya persistido. No hay automatización de rotación app en esta etapa.

`sensitive=true` oculta consola, **no impide persistencia en state/plan**. No introducir secret_version data sources ni passwords en recursos Terraform. Secrets en environment ECS pueden ser visibles a procesos/logs si la aplicación los imprime; no registrar env ni valores. Usar claves AWS managed para secretos por defecto. Claves propias: introducir sólo ARNs concretos en `secret_kms_key_arns`, con key policy compatible; IAM permite Decrypt sólo vía Secrets Manager regional.

RDS exige SSL (`rds.force_ssl=1`), JDBC `sslmode=require` evita fallback plaintext, **sin validar certificado/hostname**. Producción requiere `verify-full` y CA RDS disponible en imagen/truststore; pendiente de validar runtime, no implementado ahora. Ver [SSL RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).

## IAM y autenticación futura

Provider usa cadena normal AWS (por ejemplo SSO/perfil de rol elegido por el operador), sin credentials/account ID/profile hardcoded. Identidad Terraform temporal independiente de workloads; no se genera ni obtiene aquí.

Execution role confía sólo en `ecs-tasks.amazonaws.com`, permite `logs:CreateLogStream`/`logs:PutLogEvents` en streams del log group concreto y `secretsmanager:GetSecretValue` en dos secrets exactos. `:*` de log ARN alcanza streams de ese grupo, no todos los recursos. KMS Decrypt opcional limitado a claves explícitas y `kms:ViaService`. GHCR público no necesita ECR policies, PAT ni repositoryCredentials. Application task role ausente: ninguna API AWS requerida por backend.

Identidad de provisioning necesitará, tras revisión de seguridad de la cuenta: lectura/creación/modificación/borrado y tagging de VPC/subnets/IGW/routes/SG, ALB/listeners/target groups, cluster/service/task definitions ECS, RDS DB/subnet/parameter groups y snapshots, CloudWatch log groups, IAM execution role/policy; `iam:PassRole` sólo sobre ese execution role con condición `iam:PassedToService=ecs-tasks.amazonaws.com`. RDS managed secrets puede requerir permisos Secrets Manager/KMS y creación inicial de service-linked roles RDS/ECS/ELB. Budget añade permisos `budgets` sólo si se habilita. No se distribuye una policy de provisioning supuestamente completa sin evaluar SCPs, permission boundaries y acciones API de la cuenta. Revisar alcance/tag conditions antes de plan; workload nunca recibe AdministratorAccess. Bootstrap DB usa una identidad operativa temporal separada con lectura master, no execution role.

## Primer despliegue futuro (NO ejecutar en Etapa 8)

1. Autorizar costos/permisos, resolver HTTPS/dominio/CORS si habrá navegador/login, decidir acceso administrativo DB privado, medir capacidad y validar CA/TLS.
2. Seleccionar digest real; completar ARNs no secretos y snapshot único. Crear secretos externos por un canal seguro; no vía tfvars. Configurar `desired_count=0` inicialmente para DB/bootstrap sin tasks que fallen por credenciales ausentes. El provider no verifica existencia de secrets declarados hasta ejecución ECS.
3. Crear infraestructura sólo tras revisión del plan; conectar a DB **desde acceso autorizado dentro de la VPC** y crear usuario limitado. SG DB permite sólo SG backend: un bootstrap temporal debe usar una task/herramienta de migración con ese SG y rol operativamente revisado; no abrir 5432 público ni inventar un bastion. Este mecanismo aún debe autorizarse/diseñarse antes del despliegue.
4. Completar secrets externos, probar Flyway/TLS y pasar desired count a 1 (o 2). Revisar plan de segunda fase y health/rollback. No CD ni despliegue desde GitHub.

Comandos de referencia **futuros y no ejecutados**:

```bash
terraform plan -out=reviewed.tfplan
terraform show reviewed.tfplan
terraform apply reviewed.tfplan
```

No imprimir state/plan completo en logs públicos. Plan generado contiene datos de infraestructura potencialmente sensibles y se ignora en Git. `terraform apply` sin plan revisado no forma parte del flujo preparado.

## HTTPS, frontend y observabilidad

Vercel mantiene build estático; Vite Docker permanece laboratorio. ALB DNS HTTP sirve sólo checks restringidos. Vercel HTTPS→API HTTP provoca mixed content. Futuro: dominio autorizado, ACM validado por DNS, listener 443, redirect 80, CORS exacto de Vercel y CA RDS verify-full; no certificados/domain records todavía. Ninguna etiqueta `production` habilita esos recursos automáticamente.

Logs `prod` JSON a `/ecs/<project>-<environment>/backend`, streams `backend`, retención configurable 14 días. CloudWatch métricas nativas iniciales; no nuevas custom metrics, Container Insights ni stack Prom/Grafana AWS. Logs cifrados por CloudWatch en reposo con gestión AWS por defecto. Alarmas operativas/SLIs cloud deben definirse y probarse en siguiente etapa; el lab local conserva 50 recording rules, seis alertas y dos dashboards sin cambios.

## State, outputs y destrucción futura

State local sólo para preparación; no existe state generado con fmt/init/validate. `.terraform`, state/backups, plans, tfvars, overrides y crash logs se ignoran; `.terraform.lock.hcl` se conserva. Incluso sin passwords, state revela topología/ARN/endpoints: tratar como sensible y proteger disco/backups.

Remote state futuro: bucket S3 dedicado, versioning, encryption, Block Public Access, IAM al prefix exacto y TLS obligatorio. Terraform moderno permite bloqueo nativo con `use_lockfile=true`; [locking DynamoDB está deprecated](https://developer.hashicorp.com/terraform/language/backend/s3). Permisos state object Get/Put, lock `.tflock` Get/Put/Delete y ListBucket prefix limitado; KMS si se adopta clave propia. Bootstrap del bucket separado y autorizado; ningún backend S3 ni bucket creado aquí. No credentials dentro de backend config. No usar un state real de otro entorno.

Outputs: DNS ALB, nombres ECS cluster/service, endpoint RDS, log group y OCI inmutable. Ningún secreto. State de secret versions externos no está bajo gestión de este root.

Destroy requiere respaldo/recuperación revisados y nombre snapshot final único. Cambiar `deletion_protection=false` mediante un **apply futuro separado y autorizado**, verificar snapshot naming y después:

```bash
# Referencia futura exclusivamente: NO ejecutado en esta etapa
terraform plan -destroy -out=destroy.tfplan
terraform apply destroy.tfplan
```

Snapshot final obligatorio (`skip_final_snapshot=false`), backups automáticos conservados dentro de retención (`delete_automated_backups=false`). Los snapshots siguen generando costos después de borrar DB; si el nombre ya existe destroy falla, no desactivar snapshot para esconder el fallo. AWS elimina el master secret administrado al borrar DB; snapshot no conserva un secret operativo: recuperación requiere recrear/resetear master de forma segura. Secrets externos no son destruidos por Terraform y también facturan. Log group se borra en destroy: exportar evidencia necesaria antes, retención no es backup. ALB/IP tasks se liberan al destruir; verificar recursos/facturación reales en la etapa autorizada. Budgets notifican, no bloquean costos, y el preparado es account-wide si se activa.

## Límites de la validación

### Pausa temporal RDS frente a destrucción

Escalar ECS a cero deja de facturar CPU/RAM y las IPv4 de tasks terminadas; ALB y sus IPv4, RDS, almacenamiento, secretos y logs retenidos siguen generando cargos. No es equivalente a destruir el entorno.

[RDS permite detener PostgreSQL temporalmente](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_StopInstance.html), como máximo siete días consecutivos; después AWS lo reinicia automáticamente. Detenido no cobra horas de instancia, pero cobra storage provisionado y backups/snapshots según tarifa/cuota. Esta DB no tiene IPv4 pública. Detener RDS no elimina ALB ni sus cargos; el backend perdería readiness mientras la DB está detenida. No diseñar ahorro indefinido basado en stop ni ejecutar stop como parte de una auditoría read-only.

Un destroy futuro elimina gran parte del costo continuo después de desactivar protección conscientemente, pero conserva snapshot final, backups dentro de retención y secrets externos, según lo explicado arriba. State no desaparece por borrar recursos. Saved plans/JSON pueden revelar infraestructura: mantenerlos locales/ignorados y eliminarlos tras revisión si contienen datos sensibles. `.terraform.lock.hcl` debe permanecer.

Fmt/init/validate y revisión estática no prueban cuotas/permisos, SKU regional PostgreSQL 17+t4g.micro, health de tasks, acceso GHCR desde Fargate, montaje/runtime, restore de snapshots ni disponibilidad real. No plan/account APIs, apply, state remoto, AWS CLI, creación cloud, staging, commit o push en Etapa 8.
