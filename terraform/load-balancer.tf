resource "aws_lb" "main" {
  name                       = local.name
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = [for subnet in aws_subnet.public : subnet.id]
  drop_invalid_header_fields = true
}

resource "aws_lb_target_group" "backend" {
  name                 = "${local.name}-backend"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = aws_vpc.main.id
  deregistration_delay = 60

  health_check {
    enabled             = true
    protocol            = "HTTP"
    port                = "9091"
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# Closed SG by default; temporary technical HTTP only. HTTPS needs an authorized domain/ACM.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

resource "aws_lb_listener_rule" "hide_actuator" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  condition {
    path_pattern { values = ["/actuator", "/actuator/*"] }
  }
  action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
      message_body = "Not found"
    }
  }
}
