# Costos AWS — diseño Etapa 8

Consulta: **2026-10-04**, región **us-east-1 (N. Virginia)**, USD, on-demand, 730 horas/mes. Estimaciones, no cotización. No se utilizó una cuenta AWS. No se incluyen impuestos, créditos/free tier, descuentos ni costos de Vercel. Revisar tarifas y uso en AWS Pricing Calculator antes de autorizar un despliegue.

## Tarifas oficiales consultadas

| Concepto | Tarifa USD | Fuente |
|---|---:|---|
| Fargate Linux x86 vCPU | 0,040480/vCPU-h | [Catálogo ECS regional](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonECS/current/us-east-1/index.json), SKU `8CESGAFWKAJ98PME` |
| Fargate RAM | 0,004445/GiB-h | Mismo catálogo, SKU `PBZNQUSEXZUC34C9` |
| ALB | 0,0225/h + 0,008/LCU-h | [Catálogo ELB regional](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AWSELB/current/us-east-1/index.json), SKU `37CUWUT8GSNQEPUV` y `P2XGEJ8N3KU52WA8` |
| RDS PostgreSQL t4g.micro Single-AZ | 0,016/h | [Catálogo RDS regional](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonRDS/current/us-east-1/index.json), SKU `9HPEGXQTDDGH53C9` |
| RDS PostgreSQL t4g.small Multi-AZ | 0,065/h | Mismo catálogo, SKU `5SY529FVXY3EARD8` |
| RDS gp3 Single-AZ / Multi-AZ | 0,115 / 0,230/GiB-mes | Mismo catálogo, SKU `KYVYY29G957PKY3B` / `J7S7KD4WFDNQWKNX` |
| Backup RDS excedente/conservado | 0,095/GiB-mes | Mismo catálogo, `ChargedBackupUsage`; revisar cuota gratuita mientras DB existe |
| EC2 Linux t3.small / t3.medium | 0,0208 / 0,0416/h | [Catálogo público EC2 N. Virginia](https://b0.p.awsstatic.com/pricing/2.0/meteredUnitMaps/ec2/USD/current/ec2-ondemand-without-sec-sel/US%20East%20(N.%20Virginia)/Linux/index.json), SKU `QA3NBPZEQKZ2K9AR` / `NN4EGUUQRWVYP98C` |
| EBS gp3 básico | 0,08/GiB-mes | [Catálogo EC2 regional CSV](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/us-east-1/index.csv), SKU `JG3KUJMBRGHV3N8G`, sin IOPS/throughput adicional; [precios EBS](https://aws.amazon.com/ebs/pricing/) |
| EKS control plane estándar / extendido | 0,10 / 0,60/h | [Precios EKS](https://aws.amazon.com/eks/pricing/) |
| IPv4 pública | 0,005/IP-h | [Precios VPC](https://aws.amazon.com/vpc/pricing/) |
| NAT Gateway zonal | 0,045/h + 0,045/GiB procesado | [Precios VPC](https://aws.amazon.com/vpc/pricing/), más IPv4 y transferencia |
| CloudWatch logs Standard ingest / archivo | 0,50/GiB / 0,03/GiB-mes | [Precios CloudWatch](https://aws.amazon.com/cloudwatch/pricing/) |
| Secrets Manager | 0,40/secret-mes + 0,05/10.000 llamadas | [Precios Secrets Manager](https://aws.amazon.com/secrets-manager/pricing/) |

Catálogos consultados: ECS/ELB publicados 2026-09-11; RDS 2026-10-01; EC2 2026-09-25. Los enlaces `current` cambian; fecha y SKU permiten revisar esta estimación. [Fargate](https://aws.amazon.com/fargate/pricing/) incluye 20 GiB efímeros; no hay cargo extra de storage task en estos ejemplos.

## Lab mínimo ECS

Una task 0,5 vCPU/1 GiB, ALB dos AZ, RDS micro Single-AZ 20 GiB. Supuesto explícito de 0,1 LCU promedio, 1 GiB logs ingerido, 1 GiB promedio de logs almacenado y 10.000 llamadas Secrets/mes. Tres secrets: master RDS + DB aplicación + bootstrap/JWT. No es un pronóstico de tráfico.

| Componente | Cálculo mensual | USD |
|---|---|---:|
| Task | `(0,5 × 0,040480 + 1 × 0,004445) × 730` | 18,02 |
| ALB fijo | `0,0225 × 730` | 16,43 |
| ALB LCU supuesto | `0,1 × 0,008 × 730` | 0,58 |
| RDS micro | `0,016 × 730` | 11,68 |
| RDS gp3 | `20 × 0,115` | 2,30 |
| IPv4, mínimo 2 ALB + 1 task | `3 × 0,005 × 730` | 10,95 |
| Secrets + llamadas | `3 × 0,40 + 0,05` | 1,25 |
| CloudWatch ingest + almacenamiento supuesto | `1 × 0,50 + 1 × 0,03` | 0,53 |
| **Total sin red variable adicional** | Suma antes de redondear | **61,74** |

ALB puede consumir más IPv4 al escalar; tareas adicionales durante rollout generan CPU/RAM/IP adicionales. `desired_count=0` suspende tasks, **no** elimina cargos ALB/RDS/secrets. Un NAT añadiría USD 32,85/mes de gateway + 3,65 de IPv4 = **36,50**, más datos. Dos NAT añadirían 73,00. El lab evita ese costo de forma explícita.

## Producción razonable: ejemplo de capacidad, no SLA garantizado

Dos tasks de 1 vCPU/2 GiB en subnets privadas, RDS t4g.small Multi-AZ, 50 GiB gp3, ALB, dos NAT zonales. Supuestos: 1 LCU promedio, 10 GiB logs ingest, 1 GiB promedio archivado y 10 GiB procesados por NAT en total. HTTPS/ACM y dominio/CORS, CA RDS, alarmas y pruebas HA se requieren antes de uso real; esa topología privada no está implementada en este Terraform de laboratorio.

| Componente | USD/mes |
|---|---:|
| Dos tasks | 72,08 |
| ALB + 1 LCU | 22,27 |
| RDS small Multi-AZ | 47,45 |
| RDS gp3 Multi-AZ 50 GiB | 11,50 |
| Dos NAT | 65,70 |
| IPv4 mínimo: dos ALB + dos NAT | 14,60 |
| NAT procesa 10 GiB | 0,45 |
| Secrets + 10.000 llamadas | 1,25 |
| CloudWatch supuesto | 5,03 |
| **Total estimado** | **240,33** |

No incluye dominio/Route 53, alarmas/custom metrics, consultas Logs Insights, mayor backup, egress ni cruce AZ. Capacidad debe medirse; más replicas y Multi-AZ no garantizan por sí solos disponibilidad de la aplicación.

## Comparación EC2/EKS

Mismos supuestos RDS micro/20 GiB, secrets y logs del lab:

* **EC2 sencillo:** t3.small 15,18 + EBS 20 GiB 1,60 + una IPv4 3,65 + RDS/storage 13,98 + secrets 1,25 + logs 0,53 = **36,19/mes**. Acceso directo con reverse proxy que el operador debe gestionar; no HA. Con mismo ALB/LCU y sus dos IPv4: **60,50/mes**. Consolidar DB en EC2 podría ahorrar RDS, pero pierde la demostración de DB administrada y exige backups propios; no se toma como comparación equivalente.
* **EKS con nodos:** control plane estándar 73,00 + dos t3.medium 60,74 + EBS total 40 GiB 3,20 + ALB/LCU 17,01 + cuatro IPv4 14,60 + RDS/storage 13,98 + secrets 1,25 + logs 0,53 = **184,31/mes**, con nodos públicos restringidos, sin NAT. Addons, volúmenes de observabilidad, headroom y créditos CPU adicionales no incluidos. Nodos privados + dos NAT: incremento aproximado **65,70/mes** al sustituir IPv4 de nodos por IPv4 NAT, más tráfico. EKS Fargate requiere subnets privadas y un dimensionamiento específico de pods/CoreDNS; no se presenta una estimación falsa trasladando sin más el precio de una task ECS.

EC2 puede ser preferible bajo presupuesto estricto. ECS cuesta más que EC2 mínimo por ALB/aislamiento y gestión del runtime; elegirlo es una decisión operativa/educativa explícita, no una afirmación de menor costo.

## Variables de costo y controles

[Transferencia EC2](https://aws.amazon.com/ec2/pricing/on-demand/): AWS agrega una franquicia de 100 GiB/mes de salida a Internet entre servicios/regiones elegibles; no se asume saldo disponible. Primer tramo de salida pagada en esta región: USD 0,09/GiB. Transferencia entre AZ puede cobrarse (según camino/servicio); presupuestar hasta USD 0,01/GiB por dirección donde corresponda, además del procesamiento NAT. RDS↔ECS entre AZ y descargas desde GHCR durante rollout merecen revisar tráfico real.

[RDS T4g/T3 Unlimited](https://aws.amazon.com/rds/postgresql/pricing/) cobra créditos excedentes a USD 0,075/vCPU-h; EC2 T3 Linux Unlimited a USD 0,05/vCPU-h según [precios EC2](https://aws.amazon.com/ec2/pricing/on-demand/). Retención de snapshots después de destroy no es gratis. Ingest de logs puede crecer con errores/reintentos. Consultas, alarmas, KMS propio, CloudFront o servicios Prometheus/Grafana administrados agregan partidas. No se presupuestan como cero si se activan después.

Budget opcional preparado, deshabilitado por defecto: **USD 75/mes**, derivado del baseline 61,74 + ~20% de margen y redondeo. Notifica 80% real y 100% forecast por emails explícitos. Para el ejemplo productivo revisar al menos ~USD 300 y mediciones reales. **Un Budget no es un límite de gasto ni detiene recursos**; hay latencia de billing y alertas.

Tags comunes identifican Project/Environment/ManagedBy/Repository. Retención logs 14 días; storage RDS no crece automáticamente; cero NAT en lab. Antes del despliegue revisar presupuesto, región, cuotas y snapshot final. Tras destroy verificar inventario y facturación, snapshots, secrets externos, logs retenidos, IPs y futuro bucket state. La protección de borrado exige decisión consciente; no resolverla con un borrado apresurado de datos.
