resource "azurerm_resource_group" "principal" {
  name     = "${local.nom}-rg"
  location = var.region
  tags     = local.etiquettes
}

resource "azurerm_virtual_network" "principal" {
  name                = "${local.nom}-vnet"
  location            = azurerm_resource_group.principal.location
  resource_group_name = azurerm_resource_group.principal.name
  address_space       = [var.plage_reseau]
  tags                = local.etiquettes
}

resource "azurerm_subnet" "aks" {
  name                 = "snet-aks"
  resource_group_name  = azurerm_resource_group.principal.name
  virtual_network_name = azurerm_virtual_network.principal.name
  address_prefixes     = [cidrsubnet(var.plage_reseau, 4, 0)]
  service_endpoints    = ["Microsoft.KeyVault", "Microsoft.Storage"]
}

# Sous-réseau délégué à PostgreSQL Flexible Server : la base n'a aucune adresse publique.
resource "azurerm_subnet" "postgres" {
  name                 = "snet-postgres"
  resource_group_name  = azurerm_resource_group.principal.name
  virtual_network_name = azurerm_virtual_network.principal.name
  address_prefixes     = [cidrsubnet(var.plage_reseau, 8, 16)]
  delegation {
    name = "postgres"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_private_dns_zone" "postgres" {
  name                = "${local.nom}.postgres.database.azure.com"
  resource_group_name = azurerm_resource_group.principal.name
  tags                = local.etiquettes
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  name                  = "postgres"
  resource_group_name   = azurerm_resource_group.principal.name
  private_dns_zone_name = azurerm_private_dns_zone.postgres.name
  virtual_network_id    = azurerm_virtual_network.principal.id
}
