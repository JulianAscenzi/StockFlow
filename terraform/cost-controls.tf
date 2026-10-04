# Optional ACCOUNT-WIDE budget: avoids missing untagged fees. Alerts are not a spending cap.
resource "aws_budgets_budget" "monthly" {
  count        = var.enable_budget ? 1 : 0
  name         = "${local.name}-monthly-account"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.budget_notification_emails
  }
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = var.budget_notification_emails
  }

  lifecycle {
    precondition {
      condition     = length(var.budget_notification_emails) > 0
      error_message = "Habilitar Budget requiere al menos un email explícito."
    }
  }
}
