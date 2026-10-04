variable "region" {
  description = "Región AWS; las estimaciones usan us-east-1."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefijo corto de recursos."
  type        = string
  default     = "stockflow"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,12}$", var.project_name)) && !endswith(var.project_name, "-") && !strcontains(var.project_name, "--") && !startswith(var.project_name, "internal-")
    error_message = "Usar 2–13 caracteres minúsculos, números o guiones, comenzando con letra; sin guion final/doble ni prefijo internal-."
  }
}

variable "environment" {
  description = "Etiqueta del entorno; production no convierte esta red lab en productiva."
  type        = string
  default     = "lab"
  validation {
    condition     = contains(["lab", "production"], var.environment)
    error_message = "Environment debe ser lab o production."
  }
}

variable "vpc_cidr" {
  description = "CIDR IPv4 privado RFC1918 /16; permite cuatro subnets /24."
  type        = string
  default     = "10.42.0.0/16"
  validation {
    condition = can(cidrnetmask(var.vpc_cidr)) && endswith(var.vpc_cidr, "/16") && (
      startswith(var.vpc_cidr, "10.") || startswith(var.vpc_cidr, "192.168.") ||
      can(regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.", var.vpc_cidr))
    )
    error_message = "Usar un CIDR IPv4 privado RFC1918 /16."
  }
}

variable "availability_zones" {
  description = "Dos AZ distintas de la región; confirmar disponibilidad en la cuenta antes de plan."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
  validation {
    condition     = length(var.availability_zones) == 2 && length(distinct(var.availability_zones)) == 2 && alltrue([for az in var.availability_zones : can(regex("^${var.region}[a-z]$", az))])
    error_message = "Indicar exactamente dos AZ distintas de la región elegida."
  }
}

variable "alb_allowed_ipv4_cidrs" {
  description = "Acceso HTTP técnico restringido; vacío cierra ingress. No usar HTTP para credenciales."
  type        = set(string)
  default     = []
  validation {
    condition     = alltrue([for cidr in var.alb_allowed_ipv4_cidrs : can(cidrnetmask(cidr)) && cidr != "0.0.0.0/0"])
    error_message = "Usar CIDRs IPv4 de operadores; no se permite 0.0.0.0/0 en el listener HTTP lab."
  }
}

variable "backend_image_repository" {
  description = "Repositorio público GHCR sin tag/digest."
  type        = string
  default     = "ghcr.io/julianascenzi/stockflow-backend"
  validation {
    condition     = can(regex("^ghcr\\.io/[a-z0-9._-]+/[a-z0-9._/-]+$", var.backend_image_repository))
    error_message = "Indicar repositorio GHCR público, sin tag ni digest."
  }
}

variable "backend_image_digest" {
  description = "Digest publicado y verificado, sha256 seguido de 64 hex; nunca latest."
  type        = string
  validation {
    condition     = can(regex("^sha256:[0-9a-f]{64}$", var.backend_image_digest))
    error_message = "Digest requerido con formato sha256:<64 hex minúsculos>."
  }
}

variable "task_cpu" {
  description = "CPU units Fargate: 256, 512 o 1024."
  type        = number
  default     = 512
  validation {
    condition     = contains([256, 512, 1024], var.task_cpu)
    error_message = "CPU soportada en este laboratorio: 256, 512 o 1024."
  }
}

variable "task_memory" {
  description = "MiB; combinación validada según CPU Fargate."
  type        = number
  default     = 1024
  validation {
    condition = (
      (var.task_cpu == 256 && contains([512, 1024, 2048], var.task_memory)) ||
      (var.task_cpu == 512 && contains([1024, 2048, 3072, 4096], var.task_memory)) ||
      (var.task_cpu == 1024 && contains([2048, 3072, 4096, 5120, 6144, 7168, 8192], var.task_memory))
    )
    error_message = "Elegir un par CPU/memoria Fargate válido dentro de los tamaños soportados."
  }
}

variable "desired_count" {
  description = "0 para bootstrap/pausa de tasks, 1 lab, 2 para dos replicas. ALB/RDS siguen facturando con 0."
  type        = number
  default     = 1
  validation {
    condition     = contains([0, 1, 2], var.desired_count)
    error_message = "Desired count debe ser 0, 1 o 2."
  }
}

variable "db_instance_class" {
  description = "Clase RDS, verificar capacidad/orderability regional antes del despliegue."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "GiB gp3 sin autoscaling; ampliar conscientemente si se llena."
  type        = number
  default     = 20
  validation {
    condition     = var.db_allocated_storage >= 20 && var.db_allocated_storage <= 100 && floor(var.db_allocated_storage) == var.db_allocated_storage
    error_message = "Para este entorno usar entre 20 y 100 GiB enteros."
  }
}

variable "db_name" {
  description = "Nombre de base PostgreSQL."
  type        = string
  default     = "stockflow"
  validation {
    condition     = can(regex("^[a-z][a-z0-9]{0,62}$", var.db_name))
    error_message = "Nombre alfanumérico minúsculo, máximo 63, comenzar por letra."
  }
}

variable "multi_az" {
  description = "Standby RDS; falso para lab por costo."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Retención backups automáticos, mínimo un día para este entorno."
  type        = number
  default     = 7
  validation {
    condition     = var.backup_retention_days >= 1 && var.backup_retention_days <= 35 && floor(var.backup_retention_days) == var.backup_retention_days
    error_message = "Elegir 1–35 días enteros de backup."
  }
}

variable "deletion_protection" {
  description = "Protección RDS; desactivar sólo mediante cambio revisado antes de destroy autorizado."
  type        = bool
  default     = true
}

variable "final_snapshot_identifier" {
  description = "Nombre único del snapshot a conservar en destroy; actualizar antes de cada destrucción."
  type        = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,254}$", var.final_snapshot_identifier)) && !endswith(var.final_snapshot_identifier, "-") && !strcontains(var.final_snapshot_identifier, "--")
    error_message = "Snapshot: comenzar con letra, minúsculas/números/guiones, sin doble guion ni guion final; máximo 255."
  }
}

variable "db_application_secret_arn" {
  description = "Secret externo con username/password del usuario DB limitado; nunca master RDS."
  type        = string
  validation {
    condition     = can(regex("^arn:aws:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$", var.db_application_secret_arn))
    error_message = "Indicar ARN Secrets Manager, no el contenido secreto."
  }
}

variable "application_secret_arn" {
  description = "Secret externo JSON con adminEmail/adminPassword/jwtSecret."
  type        = string
  validation {
    condition     = can(regex("^arn:aws:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$", var.application_secret_arn))
    error_message = "Indicar ARN Secrets Manager, no el contenido secreto."
  }
}

variable "secret_kms_key_arns" {
  description = "Claves KMS propias usadas por secrets externos; vacío usa clave administrada Secrets Manager."
  type        = set(string)
  default     = []
  validation {
    condition     = alltrue([for arn in var.secret_kms_key_arns : can(regex("^arn:aws:kms:[a-z0-9-]+:[0-9]{12}:key/[a-f0-9-]+$", arn))])
    error_message = "Usar ARNs de claves KMS concretas; no wildcards."
  }
}

variable "log_retention_days" {
  description = "Retención CloudWatch; no se permite infinita."
  type        = number
  default     = 14
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365], var.log_retention_days)
    error_message = "Elegir una retención CloudWatch soportada entre 1 y 365 días."
  }
}

variable "tags" {
  description = "Tags adicionales; los tags de gobierno comunes tienen prioridad."
  type        = map(string)
  default     = {}
}

variable "enable_budget" {
  description = "Preparar presupuesto de billing opcional; requiere emails y permisos de cuenta."
  type        = bool
  default     = false
}

variable "monthly_budget_usd" {
  description = "75 USD: baseline lab 61,74 + aproximadamente 20%; revisar con uso/capacidad."
  type        = number
  default     = 75
  validation {
    condition     = var.monthly_budget_usd > 0
    error_message = "Presupuesto debe ser positivo."
  }
}

variable "budget_notification_emails" {
  description = "Destinatarios explícitos, fuera de tfvars versionados; sin SNS ni automatización de apagado."
  type        = list(string)
  default     = []
  validation {
    condition     = length(var.budget_notification_emails) <= 10 && alltrue([for email in var.budget_notification_emails : can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", email))])
    error_message = "Indicar hasta 10 emails válidos."
  }
}
