output "alb_hostname" {
  description = "DNS ALB; acceso HTTP cerrado por defecto y no apto para credenciales."
  value       = aws_lb.main.dns_name
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  value = aws_ecs_service.backend.name
}

output "rds_endpoint" {
  description = "Endpoint privado, sin credenciales."
  value       = aws_db_instance.main.endpoint
}

output "cloudwatch_log_group" {
  value = aws_cloudwatch_log_group.backend.name
}

output "backend_image" {
  description = "Referencia OCI inmutable."
  value       = local.backend_image
}
