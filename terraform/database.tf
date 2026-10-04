resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = [for subnet in aws_subnet.database : subnet.id]
}

resource "aws_db_parameter_group" "main" {
  name   = "${local.name}-postgres17"
  family = "postgres17"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "main" {
  identifier                  = local.name
  engine                      = "postgres"
  engine_version              = "17"
  instance_class              = var.db_instance_class
  db_name                     = var.db_name
  username                    = "stockflow_owner_admin"
  manage_master_user_password = true
  allocated_storage           = var.db_allocated_storage
  max_allocated_storage       = 0
  storage_type                = "gp3"
  storage_encrypted           = true
  multi_az                    = var.multi_az
  publicly_accessible         = false
  db_subnet_group_name        = aws_db_subnet_group.main.name
  parameter_group_name        = aws_db_parameter_group.main.name
  vpc_security_group_ids      = [aws_security_group.database.id]
  backup_retention_period     = var.backup_retention_days
  backup_window               = "03:00-04:00"
  maintenance_window          = "sun:04:00-sun:05:00"
  deletion_protection         = var.deletion_protection
  skip_final_snapshot         = false
  final_snapshot_identifier   = var.final_snapshot_identifier
  copy_tags_to_snapshot       = true
  delete_automated_backups    = false
  auto_minor_version_upgrade  = true
  allow_major_version_upgrade = false
  apply_immediately           = false
}
