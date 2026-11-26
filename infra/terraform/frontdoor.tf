# Front Door : entrée publique unique, TLS géré ; le WAF OWASP est appliqué à l'entrée du cluster.
resource "azurerm_cdn_frontdoor_profile" "principal" {
  name                = "${local.nom}-afd"
  resource_group_name = azurerm_resource_group.principal.name
  sku_name            = "Standard_AzureFrontDoor"
  tags                = local.etiquettes
}

resource "azurerm_cdn_frontdoor_endpoint" "api" {
  name                     = "${local.nom}-api"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.principal.id
  tags                     = local.etiquettes
}

resource "azurerm_cdn_frontdoor_origin_group" "aks" {
  name                     = "aks"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.principal.id
  load_balancing {
    sample_size                 = 4
    successful_samples_required = 3
  }
  health_probe {
    path                = "/actuator/health"
    protocol            = "Https"
    interval_in_seconds = 30
    request_type        = "GET"
  }
}

resource "azurerm_cdn_frontdoor_custom_domain" "api" {
  name                     = "api"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.principal.id
  host_name                = var.nom_hote_api
  tls {
    certificate_type = "ManagedCertificate"
  }
}
