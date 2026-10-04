resource "aws_iam_role" "execution" {
  name = "${local.name}-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "execution" {
  name = "${local.name}-execution"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat([
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = ["${aws_cloudwatch_log_group.backend.arn}:*"]
      },
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = [var.db_application_secret_arn, var.application_secret_arn]
      }
      ], length(var.secret_kms_key_arns) == 0 ? [] : [{
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = sort(tolist(var.secret_kms_key_arns))
        Condition = {
          StringEquals = { "kms:ViaService" = "secretsmanager.${var.region}.amazonaws.com" }
        }
    }])
  })
}

# No application task role: backend does not call AWS APIs.
# No ECR permissions/registry credentials: GHCR images are public.
