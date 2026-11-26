# Reprise (VS-113) : réplique en lecture de la base dans la région de reprise, promue en cas de sinistre.
resource "azurerm_resource_group" "reprise" {
  count    = var.region_reprise == null ? 0 : 1
  name     = "${local.nom}-reprise-rg"
  location = var.region_reprise
  tags     = local.etiquettes
}

resource "azurerm_postgresql_flexible_server" "reprise" {
  count               = var.region_reprise == null ? 0 : 1
  name                = "${local.nom}-pg-reprise"
  resource_group_name = azurerm_resource_group.reprise[0].name
  location            = var.region_reprise
  create_mode         = "Replica"
  source_server_id    = azurerm_postgresql_flexible_server.principal.id
  version             = var.postgres_version
  sku_name            = var.postgres_sku
  storage_mb          = var.postgres_stockage_mo
  tags                = local.etiquettes

  lifecycle {
    ignore_changes = [zone]
  }
}
