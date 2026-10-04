resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "Restricted technical HTTP listener; backend-only egress"
  vpc_id      = aws_vpc.main.id
}

resource "aws_security_group" "backend" {
  name        = "${local.name}-backend"
  description = "Only ALB ingress; HTTPS and PostgreSQL egress"
  vpc_id      = aws_vpc.main.id
}

resource "aws_security_group" "database" {
  name        = "${local.name}-database"
  description = "PostgreSQL only from backend; no outbound initiation"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  for_each          = var.alb_allowed_ipv4_cidrs
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  description       = "Operator technical checks only; no credentials over HTTP"
}

resource "aws_vpc_security_group_ingress_rule" "backend_from_alb" {
  for_each                     = toset(["8080", "9091"])
  security_group_id            = aws_security_group.backend.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

resource "aws_vpc_security_group_egress_rule" "alb_to_backend" {
  for_each                     = toset(["8080", "9091"])
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.backend.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

resource "aws_vpc_security_group_egress_rule" "backend_https" {
  security_group_id = aws_security_group.backend.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "GHCR/CDN and AWS APIs; public DNS destinations vary"
}

resource "aws_vpc_security_group_egress_rule" "backend_database" {
  security_group_id            = aws_security_group.backend.id
  referenced_security_group_id = aws_security_group.database.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_vpc_security_group_ingress_rule" "database_backend" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = aws_security_group.backend.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
