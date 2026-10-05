# ADR 0001 — Runtime cloud de StockFlow

Fecha: 2026-10-04. Estado: aceptado para preparación; despliegue AWS no autorizado.

## Contexto

StockFlow ya demuestra Kubernetes, Helm, rolling updates, rollback y SRE mediante kind. Las imágenes públicas GHCR se identifican por digest y tienen SBOM/provenance. El diseño cloud aporta networking, IAM, compute y PostgreSQL administrados, secretos y control de costos como decisiones revisables. Su implementación Terraform se valida estáticamente; provisionar AWS es una extensión opcional, deliberadamente fuera del cierre de portfolio por costos. No se necesita otro orquestador sólo para ampliar la lista de tecnologías.

## Alternativas

Comparación en `us-east-1`, 730 horas/mes, on-demand, sin créditos ni impuestos. Los supuestos, tarifas oficiales y cálculos están en [AWS_COSTS](../AWS_COSTS.md). Los ejemplos tienen capacidades distintas; no son benchmarks ni presupuestos de producción garantizados.

| Aspecto | ECS Fargate + RDS | EKS + RDS | EC2 sencillo + RDS |
|---|---|---|---|
| Laboratorio estimado | USD 61,74/mes | USD 184,31/mes | USD 36,19/mes sin ALB; 60,50 con ALB |
| Costo fijo principal | ALB, RDS, tasks e IPv4 | Lo anterior más control plane, nodos y EBS | Instancia, disco y RDS |
| Variable | CPU/RAM por tiempo, LCU, logs, transferencia | Nodos/pods, tráfico, observabilidad, addons | Créditos CPU, tráfico, logs y discos |
| Operación | AWS administra hosts; operar servicios, IAM, capacidad y DB | Operar versiones, addons, nodos y workloads | Parches, Docker, sistema operativo, despliegues y recuperación propios |
| Seguridad | Roles separados y SG por servicio; menor acceso al host | Más RBAC/IAM y componentes; no implica seguridad automática | Mayor responsabilidad del operador; evitar SSH público, usar SSM si se elige |
| Reutilización | Imagen backend, variables, probes, CI y experiencia de recursos | Helm reutilizable parcialmente; DB y observabilidad cloud requieren adaptación | Docker/Compose reutilizables parcialmente; no usar Vite como servidor productivo |
| Reproducibilidad | IaC + definición task + digest | IaC + Helm + digest + versiones de addons | IaC + bootstrap/patching reproducible adicionales |
| Portfolio | Amplía la experiencia con servicios administrados | Profundiza Kubernetes ya demostrado | Demuestra operación pragmática a menor costo |
| Destrucción/riesgos | Snapshot y secretos pueden seguir facturando | Además: nodos, volúmenes, LB de controladores y versiones extendidas | EBS, snapshots e IPs olvidadas; host único sin HA |

EKS Fargate elimina gestión de nodos, pero sus pods requieren subnets privadas y salida para GHCR; NAT/endpoints, CoreDNS y overhead de pods deben presupuestarse. No equivale al precio de una sola task ECS. EKS cobra USD 73/mes de control plane en soporte estándar; USD 438/mes en soporte extendido. Ver [precios EKS](https://aws.amazon.com/eks/pricing/).

## Decisión y razones

Elegir **ECS Fargate Linux/amd64 + ALB + RDS PostgreSQL 17** para el laboratorio principal. Evita mantenimiento de hosts, aporta una experiencia AWS distinta de kind, utiliza el backend publicado sin reconstruirlo y mantiene una superficie de IaC pequeña. No se crea EKS ni CD. EC2 no se descarta por simplicidad: es mejor candidato si el objetivo prioritario pasa a ser costo mínimo y se acepta administrar hosts. EKS no compensa hoy su costo/operación adicional.

### Frontend

Conservar el **frontend estático en Vercel**, que ya funciona como deployment de la aplicación. La imagen frontend actual ejecuta Vite para laboratorio; no se incorpora a ECS. S3 + CloudFront es una alternativa apropiada para una futura demostración completamente AWS, pero añade distribución, políticas, caché y publicación de assets sin mejorar el objetivo inicial. Un contenedor frontend requeriría runtime productivo y facturaría otra task; se descarta en esta etapa.

Vercel sirve HTTPS: un navegador no puede consumir una API ALB HTTP por mixed content. El listener HTTP preparado es sólo una prueba técnica restringida por CIDR, cerrada por defecto, sin autenticación comercial ni credenciales transmitidas en texto claro. **Antes de conectar el frontend**: dominio autorizado, ACM, listener HTTPS, CORS exacto y revisión de proxy/`VITE_API_BASE_URL`. No se modifica Vercel ni la aplicación ahora. S3/CloudFront también necesitaría resolver HTTPS del origen API.

### Red y disponibilidad

VPC `10.42.0.0/16`, dos AZ configurables, dos subnets públicas `/24` para ALB/tasks y dos subnets DB `/24` sin ruta al Internet Gateway. Tasks con IP pública para descargar GHCR y acceder a Secrets Manager/CloudWatch, sin NAT. SG backend acepta 8080 y 9091 **sólo desde SG ALB**; DB 5432 sólo desde SG backend. Salida de tasks: HTTPS e ingreso a PostgreSQL. Esta elección ahorra NAT, no constituye el diseño ideal de producción.

Producción: dos tasks distribuidas en dos AZ, subnets compute privadas, salida NAT por AZ (o alternativa de egress revisada), RDS Multi-AZ, HTTPS y validación TLS de identidad RDS. Endpoints privados AWS no resuelven por sí solos la descarga desde GHCR. La variante privada/NAT y HTTPS se diseñan, pero **no están implementadas como opciones del Terraform inicial**.

`desired_count=1` y `multi_az=false` reducen costos; topología en dos AZ no convierte una task ni una DB Single-AZ en HA. `desired_count=2` permite dos tasks y `multi_az=true` activa standby RDS, pero no reemplaza una prueba real de fallos ni los restantes requisitos productivos. Durante rolling update puede haber hasta el doble de tasks y facturación temporal adicional.

### Base de datos y secretos

RDS PostgreSQL major 17, `db.t4g.micro`, gp3 20 GiB sin autoscaling, cifrado, backups siete días, protección de borrado y snapshot final obligatorio. [RDS publica soporte PostgreSQL 17](https://docs.aws.amazon.com/AmazonRDS/latest/PostgreSQLReleaseNotes/postgresql-versions.html); compatibilidad exacta clase/minor/AZ y cuotas en la cuenta quedan para un plan autorizado. Minor seleccionado por AWS y actualizaciones automáticas menores, sin upgrade major automático.

Elegir Secrets Manager: integra la contraseña master administrada por RDS y permite inyectar claves JSON en ECS. SSM Standard reduce costo, pero no sustituye esa integración/rotación. El master se genera **en AWS en un apply futuro**, sin `password` Terraform. El backend recibe un usuario DB limitado a StockFlow/Flyway, nunca el master, desde un secret externo. Otro secret externo contiene bootstrap admin/JWT. No hay valores secretos, generadores de contraseñas ni lecturas de secret versions en Terraform. `sensitive=true` sólo oculta presentación; no elimina valores del state. Ver [RDS y Secrets Manager](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html).

Las credenciales ECS se inyectan al inicio y su rotación exige reemplazar tasks; cambiar el contenido de un secret no provoca automáticamente deployment. Las credenciales bootstrap de administrador no equivalen a un mecanismo de rotación del administrador existente: verificar el comportamiento de la aplicación antes de rotarlas. DB requiere TLS; laboratorio `sslmode=require` cifra sin verificar identidad. `verify-full` y CA RDS en runtime son un prerrequisito productivo pendiente, sin modificar ahora la imagen.

### IAM, salud y observabilidad

Execution role: sólo lectura de los dos secrets externos y escritura de streams en el log group creado por Terraform. No necesita ECR ni PAT para GHCR público. Application task role omitido: el backend no consume APIs AWS. Identidad Terraform separada y temporal vía cadena normal AWS/SSO; permisos de provisioning y `iam:PassRole` revisados antes del despliegue, nunca `AdministratorAccess` para workloads.

ALB balancea 8080, comprueba **readiness en 9091**, y no publica listener management. Health de contenedor comprueba liveness en localhost; prod expone sólo health y no detalles. Graceful stop 60 segundos y deregistration 60 segundos; deployment circuit breaker con rollback. ALB puede fall-open si todos los targets están unhealthy; readiness no es una frontera de autorización.

CloudWatch logs JSON `prod`, retención 14 días y métricas nativas ECS/ALB/RDS inicialmente. Sin Container Insights ni metrics custom por defecto. Prometheus/Grafana/Alertmanager, reglas y dashboards locales permanecen intactos; servicios administrados de observabilidad y stack propia AWS añadirían costos y operación sin necesidad inicial.

## Consecuencias y reconsideración

El IaC inicial es un laboratorio revisable, no una plataforma productiva completa. Depende de secrets externos, bootstrap DB, conexión TLS verificada futura y autorización de costos. Un snapshot conservado genera cargos después de destroy. Sin atomicidad de despliegue cloud ni CD. Vercel sigue siendo un proveedor adicional.

Reconsiderar EKS ante múltiples servicios/equipos, necesidad real de ecosistema Kubernetes, políticas/operators/portabilidad que ECS no satisfaga o un presupuesto aprobado para operar Kubernetes administrado. Reconsiderar EC2 si el presupuesto pesa más que la operación administrada.
