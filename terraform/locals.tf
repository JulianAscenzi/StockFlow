locals {
  name          = "${var.project_name}-${var.environment}"
  backend_image = "${var.backend_image_repository}@${var.backend_image_digest}"
  tags = merge(var.tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Repository  = "https://github.com/julianascenzi/StockFlow"
  })
  zones = { for index, az in var.availability_zones : az => index }
}
