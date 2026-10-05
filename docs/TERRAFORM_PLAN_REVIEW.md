# Etapa 9A — revisión previa a plan AWS

> Checkpoint histórico de preparación 9A. No hubo plan real ni recursos AWS; la validación de cuenta es opcional y queda fuera del cierre de portfolio actual.

Estado: **pendiente de AWS CLI e identidad disponible**. Fecha: 2026-10-04. Esta revisión local no sustituye un plan real y no autoriza apply.

## Evidencia actual

Inicio limpio en `4367a2d`; staging vacío y `git diff --check` aprobado. Sin state ni saved plans fuera de `.terraform/`; sin credenciales potenciales en cambios. Terraform 1.16.5 disponible en `/tmp/stockflow-stage8/bin/terraform`, fuera de PATH; provider AWS 6.67.0 fijado por lockfile, sin actualización.

AWS CLI no encontrado en PATH ni ubicaciones habituales; `aws --version` y `aws sts get-caller-identity --region us-east-1 --no-cli-pager` devolvieron `command not found`. No hay variables `AWS_*` ni archivos estándar `~/.aws/config`/`~/.aws/credentials`. No se leyeron/imprimieron valores de credenciales, no se instalaron herramientas ni se cambió identidad/configuración global. Se solicitó únicamente ubicación del ejecutable y perfil/sesión existente, sin claves ni tokens.

Cuenta, ARN/tipo de identidad, permisos, VPC/CIDRs existentes, AZ/ZoneIds, quotas, Fargate/ECS/ALB y combinaciones orderable PostgreSQL 17/t4g.micro/gp3 **no confirmados**. Región objetivo Terraform: us-east-1; flag AWS previsto explícitamente igual. No se interpreta ausencia de datos como cuenta limpia, permisos suficientes o disponibilidad regional.

Validaciones ejecutadas ahora:

| Check | Resultado |
|---|---|
| `terraform fmt -check -recursive` | aprobado |
| `terraform init -backend=false -input=false -lockfile=readonly` | aprobado; reusa provider instalado, sin backend remoto ni upgrade |
| `terraform validate` | aprobado |
| `terraform test` | 7 pasaron, 0 fallaron; AWS mock, planes estructurales en memoria |
| Auditoría efectos | sin provisioners, local/remote-exec, data sources externos, imports, actions ni hooks; sólo provider AWS oficial |
| `terraform plan` contra AWS | **no ejecutado**: identidad no confirmada |
| Saved plan / show JSON | no generados |

El `curl` de ecs.tf es health check del contenedor futuro, no un comando ejecutado durante plan. No existe state previo que se haya importado/migrado. El init no autentica contra AWS; los tests mock no comprueban permisos ni existencia de recursos.

## Inputs preparados localmente

`terraform/terraform.tfvars`, ignorado, modo 600, sólo configuración no secreta. Defaults lab, desired 1, budget false, HTTP ingress vacío, digest backend validado previamente:

```text
ghcr.io/julianascenzi/stockflow-backend@sha256:9e27f5c5d0a9237d52124209a2f2941c2bf1ad2457f7bbf4cb6921c214945c6a
```

Dos ARNs de secrets deliberadamente ficticios con cuenta `000000000000`, no atribuidos al usuario. No se resuelven valores secretos en el código; su admisibilidad durante plan real todavía no se ha demostrado. No se crearon secrets. Master RDS sería generado por AWS en un apply futuro, fuera del alcance. AZs us-east-1a/b son defaults configurables, **no disponibles confirmadas**; revisar nombres y ZoneIds reales antes de plan, sin cambiar por preferencia.

Los inputs placeholder impiden tratar incluso un eventual plan limpio como autorización de deployment operativo. El acceso DB para bootstrap, credenciales app limitadas y HTTPS siguen requiriendo preparación y autorización independientes.

## Inventario estático — NO inventario de plan real

Con los defaults y dos AZ, el código describe **37 instancias de recursos Terraform**. No se publica `Plan: 37 to add` porque no se ejecutó ese plan. Adds/changes/destroys reales: **no disponibles**.

| Tipo Terraform | Cantidad estática |
|---|---:|
| aws_vpc | 1 |
| aws_subnet | 4 |
| aws_internet_gateway | 1 |
| aws_route_table | 3 |
| aws_route | 1 |
| aws_route_table_association | 4 |
| aws_security_group | 3 |
| aws_vpc_security_group_ingress_rule | 3 |
| aws_vpc_security_group_egress_rule | 4 |
| aws_ecs_cluster | 1 |
| aws_ecs_task_definition | 1 |
| aws_ecs_service | 1 |
| aws_lb | 1 |
| aws_lb_target_group | 1 |
| aws_lb_listener | 1 |
| aws_lb_listener_rule | 1 |
| aws_db_instance | 1 |
| aws_db_subnet_group | 1 |
| aws_db_parameter_group | 1 |
| aws_cloudwatch_log_group | 1 |
| aws_iam_role | 1 |
| aws_iam_role_policy | 1 |
| aws_budgets_budget | 0 |
| NAT Gateway / EIP explícito / secret resources / frontend ECS | 0 |

AWS puede crear componentes administrados implícitos en un apply futuro (ENIs, master secret RDS, IPv4, service-linked roles); no se confunden con recursos independientes administrados por este root. Budget está deshabilitado; si se activa después alerta, no bloquea gastos.

## Seguridad y disponibilidad revisadas en código

* ALB internet-facing, dos subnets públicas, listener HTTP 80 y SG sin ingress por defecto: **inaccesible externamente inicialmente**, aunque facturaría. No se abre HTTP para hacer una prueba verde.
* Target group HTTP/ip 8080; readiness `/actuator/health/readiness` sobre 9091, matcher 200. SG ALB puede salir a SG backend en 8080/9091; SG backend acepta sólo SG ALB en esos puertos. No listener público 9091; listener bloquea `/actuator` y `/actuator/*`.
* SG backend → SG DB 5432 y SG DB acepta sólo SG backend. Sin ingress CIDR a backend/DB ni 0.0.0.0/0 en 5432/9091/8080. Único outbound global SG: HTTPS 443 de backend para GHCR/CDN y servicios AWS. Ruta Internet sólo en subnets públicas; DB aislada.
* RDS PostgreSQL 17, t4g.micro, gp3 20 GiB, storage encrypted true, publicly_accessible false, deletion protection true, skip final snapshot false, backups 7, Multi-AZ false, storage autoscaling desactivado. Support/orderability regional no verificados contra cuenta.
* IAM sin Action `*` ni Resource `*` global. Wildcard `log-group-ARN:*` limitado a streams del grupo; lectura sólo de dos ARNs concretos. KMS opcional vacío en lab. Application role ausente; execution role no recibe AdministratorAccess ni ECR permisos.
* Terraform no define ni lee passwords/secret versions/JWT; outputs sólo identificadores. Inputs locales contienen digest y ARNs, no valores secretos. Sin plan real no se afirma inspección de su contenido sensible.
* Backend por digest Linux/amd64; frontend estático Vercel, ningún contenedor frontend ECS. Sin cambios runtime, GitHub Actions, OIDC o CD.

## Cost gate pendiente de plan real

**Costo estimado mensual antes de apply basado en plan real: pendiente.**

**Costo estimado por 24 horas basado en plan real: pendiente.**

No se actualizaron cifras de [AWS_COSTS](AWS_COSTS.md) porque no hay plan real ni cantidades reales distintas comprobadas. Referencia histórica Etapa 8: USD 61,74/mes, equivalente aritmético USD 2,03 por 24 h al prorratear 730 h/mes; **no es una cotización actual, un rango validado ni la factura de un deployment de un día**. Logs/LCU/API calls, arranque/rollout, snapshot final, créditos CPU y transferencia no necesariamente prorratean ese escenario. El baseline incluye dos secrets externos además del master; Terraform no crea esos dos secrets.

Por diseño habría mínimo **tres IPv4 cobrables**: dos ALB (dos AZ) y una task. ALB puede asignar más al escalar; rolling permite dos tasks temporalmente (mínimo cuatro IPs con ALB), o más si desired aumenta. No NAT ni EIP explícito; no inferir cero cargo IPv4 por ausencia de aws_eip. [AWS cobra IPv4 pública](https://aws.amazon.com/vpc/pricing/); ya estaba contemplada en la estimación histórica.

| Categoría | Componentes según diseño, no plan real |
|---|---|
| Continuo mientras provisionados | ALB, RDS compute, CPU/RAM tasks activas, IPv4 ALB/tasks |
| Almacenamiento/uso | gp3, backups/snapshots excedentes, CloudWatch ingest/archive/queries, Secrets Manager, LCU, transferencia y CPU credits |
| Sin cargo directo relevante en capacidades básicas usadas | VPC/subnets/route tables/SG/IGW, IAM roles/policies, ECS cluster/task definition sin tasks; no se activan IPAM avanzado, VPC Route Server u otras capacidades pagas |

Escalar ECS a cero elimina CPU/RAM e IPs de tasks terminadas, pero mantiene ALB/IPv4, RDS/storage, secrets y logs almacenados. No hacerlo en esta auditoría. [RDS stop](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_StopInstance.html) no cobra compute detenido, mantiene storage/backups y **reinicia automáticamente tras siete días**; no es ahorro indefinido ni se ejecutó stop.

## Destrucción teórica y continuación

No se ejecutó destroy ni plan destroy. Un destroy autorizado futuro elimina tasks/ALB y la mayoría de recursos de este root; RDS está protegido y requiere desactivar protección mediante cambio separado autorizado. Snapshot final conserva datos y factura; backups automáticos pueden permanecer dentro de retención. Secrets externos no gestionados por root permanecen y facturan; AWS elimina master secret administrado al borrar DB. Log group se borra (exportar evidencia requerida antes). State y futuro bucket state no desaparecen automáticamente. Ver [guía Terraform](../terraform/README.md).

Para continuar: AWS CLI existente y sesión/perfil normal disponible, STS correcto, consultas read-only EC2/AZ/quotas/RDS/ECS/ALB e IAM cuando permitidas; confirmar cuenta/región y AZ; recién entonces plan guardado en archivo ignorado y show local. Si hay changes/destroys inesperados, NAT, DB pública o material secreto: detenerse y revisar. No asumir que plan exitoso prueba permisos de creación ni que garantiza costo/capacidad/runtime.

Artefactos: no saved plan, show JSON ni state generados. `.terraform.lock.hcl` preservado; tfvars no secreto local ignorado permanece preparado. JSON `*.tfplan.json`/`*.plan.json` ahora ignorados. Ningún recurso AWS creado, modificado ni eliminado; ningún apply/destroy/AWS CLI de escritura. Staging/commit/push no ejecutados. **Etapa 9A no validada contra AWS todavía.**
