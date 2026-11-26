# PostgreSQL 16, privé, chiffré ; RLS appliquée par les migrations (VS-005). L'application se connecte avec le compte
# de migration puis endosse le rôle applicatif, soumis à la RLS.
resource "random_password" "base" {
  length  = 40
  special = false
}

resource "azurerm_postgresql_flexible_server" "principal" {
  name                          = "${local.nom}-pg"
  resource_group_name           = azurerm_resource_group.principal.name
  location                      = azurerm_resource_group.principal.location
  version                       = var.postgres_version
  sku_name                      = var.postgres_sku
  storage_mb                    = var.postgres_stockage_mo
  administrator_login           = "vinasign_admin"
  administrator_password        = random_password.base.result
  delegated_subnet_id           = azurerm_subnet.postgres.id
  private_dns_zone_id           = azurerm_private_dns_zone.postgres.id
  public_network_access_enabled = false
  zone                          = length(var.zones) > 0 ? var.zones[0] : null
  backup_retention_days         = var.retention_sauvegardes_jours
  geo_redundant_backup_enabled  = var.region_reprise != null
  auto_grow_enabled             = true

  dynamic "high_availability" {
    for_each = var.postgres_haute_disponibilite ? [1] : []
    content {
      mode                      = length(var.zones) > 1 ? "ZoneRedundant" : "SameZone"
      standby_availability_zone = length(var.zones) > 1 ? var.zones[1] : null
    }
  }

  tags       = local.etiquettes
  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]

  lifecycle {
    ignore_changes = [zone, high_availability[0].standby_availability_zone]
  }
}

resource "azurerm_postgresql_flexible_server_database" "vinasign" {
  name      = var.base_nom
  server_id = azurerm_postgresql_flexible_server.principal.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# Journalisation des connexions : la trace d'accès exigée par le dossier de sécurité, sans contenu de requête.
resource "azurerm_postgresql_flexible_server_configuration" "connexions" {
  for_each  = toset(["log_connections", "log_disconnections"])
  name      = each.value
  server_id = azurerm_postgresql_flexible_server.principal.id
  value     = "on"
}
