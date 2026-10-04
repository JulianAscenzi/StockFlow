resource "aws_ecs_cluster" "main" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.task_cpu)
  memory                   = tostring(var.task_memory)
  execution_role_arn       = aws_iam_role.execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "backend"
    image     = local.backend_image
    essential = true
    # Existing image needs writable /tmp. ECS empty volumes default to root ownership;
    # readonly root + non-root writable volume needs a separately validated design.
    readonlyRootFilesystem = false
    user                   = "stockflow"
    stopTimeout            = 60
    linuxParameters        = { initProcessEnabled = true, capabilities = { drop = ["ALL"] } }
    portMappings = [
      { containerPort = 8080, hostPort = 8080, protocol = "tcp" },
      { containerPort = 9091, hostPort = 9091, protocol = "tcp" }
    ]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "APP_AUTH_ENABLED", value = "true" },
      { name = "POSTGRES_HOST", value = aws_db_instance.main.address },
      { name = "POSTGRES_PORT", value = "5432" },
      { name = "POSTGRES_DB", value = var.db_name },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${var.db_name}?sslmode=require" },
      { name = "MANAGEMENT_SERVER_PORT", value = "9091" },
      { name = "MANAGEMENT_SERVER_ADDRESS", value = "0.0.0.0" },
      { name = "MANAGEMENT_ENDPOINTS_WEB_DISCOVERY_ENABLED", value = "false" },
      { name = "SERVER_SHUTDOWN", value = "graceful" },
      { name = "SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE", value = "30s" }
    ]
    secrets = [
      { name = "POSTGRES_USER", valueFrom = "${var.db_application_secret_arn}:username::" },
      { name = "POSTGRES_PASSWORD", valueFrom = "${var.db_application_secret_arn}:password::" },
      { name = "APP_ADMIN_EMAIL", valueFrom = "${var.application_secret_arn}:adminEmail::" },
      { name = "APP_ADMIN_PASSWORD", valueFrom = "${var.application_secret_arn}:adminPassword::" },
      { name = "APP_JWT_SECRET", valueFrom = "${var.application_secret_arn}:jwtSecret::" }
    ]
    healthCheck = {
      command     = ["CMD-SHELL", "curl --fail --silent http://localhost:9091/actuator/health/liveness > /dev/null || exit 1"]
      interval    = 10
      timeout     = 5
      retries     = 3
      startPeriod = 180
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.backend.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "backend"
      }
    }
  }])

}

resource "aws_ecs_service" "backend" {
  name                               = "${local.name}-backend"
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.backend.arn
  desired_count                      = var.desired_count
  launch_type                        = "FARGATE"
  platform_version                   = "1.4.0"
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 180
  enable_execute_command             = false
  propagate_tags                     = "SERVICE"
  wait_for_steady_state              = true

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = [for subnet in aws_subnet.public : subnet.id]
    security_groups  = [aws_security_group.backend.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  depends_on = [
    aws_lb_listener.http,
    aws_lb_listener_rule.hide_actuator,
    aws_iam_role_policy.execution,
    aws_route.internet,
    aws_route_table_association.public,
    aws_vpc_security_group_ingress_rule.backend_from_alb,
    aws_vpc_security_group_ingress_rule.database_backend,
    aws_vpc_security_group_egress_rule.backend_https,
    aws_vpc_security_group_egress_rule.backend_database,
    aws_vpc_security_group_egress_rule.alb_to_backend
  ]
}
