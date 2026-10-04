# Offline structural plans only: all AWS resources use Terraform's mock provider.
# No real provider configuration, credentials, AWS APIs or apply operation.
mock_provider "aws" {
  override_during = plan

  mock_resource "aws_db_instance" {
    override_during = plan
    defaults = {
      address = "database.example.invalid"
    }
  }
  mock_resource "aws_cloudwatch_log_group" {
    override_during = plan
    defaults = {
      arn = "arn:aws:logs:us-east-1:000000000000:log-group:/ecs/mock/backend"
    }
  }
}

override_resource {
  target          = aws_security_group.backend
  override_during = plan
  values          = { id = "sg-00000000000000001" }
}

override_resource {
  target          = aws_security_group.database
  override_during = plan
  values          = { id = "sg-00000000000000002" }
}

override_resource {
  target          = aws_security_group.alb
  override_during = plan
  values          = { id = "sg-00000000000000003" }
}

variables {
  backend_image_digest      = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  db_application_secret_arn = "arn:aws:secretsmanager:us-east-1:000000000000:secret:mock/database-ABCDEF"
  application_secret_arn    = "arn:aws:secretsmanager:us-east-1:000000000000:secret:mock/application-ABCDEF"
  final_snapshot_identifier = "stockflow-lab-final-test"
}

run "lab_safety" {
  command = plan

  assert {
    condition = (
      !aws_db_instance.main.publicly_accessible && aws_db_instance.main.storage_encrypted &&
      aws_db_instance.main.manage_master_user_password && aws_db_instance.main.password == null &&
      aws_db_instance.main.deletion_protection && !aws_db_instance.main.skip_final_snapshot
    )
    error_message = "DB must remain private, encrypted, protected and without Terraform-managed password."
  }
  assert {
    condition = (
      length(aws_subnet.public) == 2 && length(aws_subnet.database) == 2 &&
      length(aws_vpc_security_group_ingress_rule.alb_http) == 0 &&
      aws_vpc_security_group_ingress_rule.database_backend.referenced_security_group_id == aws_security_group.backend.id
    )
    error_message = "Default network must close public HTTP and limit DB ingress to backend."
  }
  assert {
    condition = (
      jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].image == "ghcr.io/julianascenzi/stockflow-backend@${var.backend_image_digest}" &&
      alltrue([for secret in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].secrets : startswith(secret.valueFrom, "arn:aws:secretsmanager:")]) &&
      aws_lb_target_group.backend.health_check[0].path == "/actuator/health/readiness" &&
      aws_lb_target_group.backend.health_check[0].port == "9091"
    )
    error_message = "OCI digest, external secret references and ALB readiness port must remain intact."
  }
  assert {
    condition = (
      toset(jsondecode(aws_iam_role_policy.execution.policy).Statement[1].Resource) == toset([var.db_application_secret_arn, var.application_secret_arn]) &&
      length(jsondecode(aws_iam_role_policy.execution.policy).Statement) == 2 &&
      length(aws_budgets_budget.monthly) == 0 && aws_cloudwatch_log_group.backend.retention_in_days == 14
    )
    error_message = "Execution role must scope secrets; budget remains opt-in and logs have retention."
  }
}

run "two_replicas_multi_az" {
  command = plan
  variables {
    desired_count = 2
    multi_az      = true
    task_cpu      = 1024
    task_memory   = 2048
  }
  assert {
    condition     = aws_ecs_service.backend.desired_count == 2 && aws_db_instance.main.multi_az && aws_ecs_task_definition.backend.memory == "2048"
    error_message = "HA/capacity overrides must affect resources."
  }
}

run "reject_public_http" {
  command = plan
  variables {
    alb_allowed_ipv4_cidrs = ["0.0.0.0/0"]
  }
  expect_failures = [var.alb_allowed_ipv4_cidrs]
}

run "reject_invalid_fargate_pair" {
  command = plan
  variables {
    task_cpu    = 512
    task_memory = 512
  }
  expect_failures = [var.task_memory]
}

run "reject_mutable_image" {
  command = plan
  variables {
    backend_image_digest = "latest"
  }
  expect_failures = [var.backend_image_digest]
}

run "reject_budget_without_recipient" {
  command = plan
  variables {
    enable_budget = true
  }
  expect_failures = [aws_budgets_budget.monthly]
}

run "budget_and_custom_secret_key" {
  command = plan
  variables {
    enable_budget              = true
    budget_notification_emails = ["operator@example.invalid"]
    secret_kms_key_arns        = ["arn:aws:kms:us-east-1:000000000000:key/00000000-0000-0000-0000-000000000000"]
  }
  assert {
    condition = (
      length(aws_budgets_budget.monthly) == 1 &&
      length(jsondecode(aws_iam_role_policy.execution.policy).Statement) == 3 &&
      jsondecode(aws_iam_role_policy.execution.policy).Statement[2].Action[0] == "kms:Decrypt" &&
      jsondecode(aws_iam_role_policy.execution.policy).Statement[2].Condition.StringEquals["kms:ViaService"] == "secretsmanager.us-east-1.amazonaws.com"
    )
    error_message = "Opt-in budget and KMS must remain scoped and usable."
  }
}
