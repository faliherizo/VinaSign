# Images signées (cosign) et analysées avant publication ; aucun compte administrateur.
resource "azurerm_container_registry" "principal" {
  name                = "${local.nom_compact}acr"
  resource_group_name = azurerm_resource_group.principal.name
  location            = azurerm_resource_group.principal.location
  sku                 = "Premium"
  admin_enabled       = false
  tags                = local.etiquettes
}
