# Identité de l'application, fédérée au compte de service Kubernetes (Workload Identity) : aucun secret à distribuer.
resource "azurerm_user_assigned_identity" "application" {
  name                = "${local.nom}-application"
  location            = azurerm_resource_group.principal.location
  resource_group_name = azurerm_resource_group.principal.name
  tags                = local.etiquettes
}

resource "azurerm_federated_identity_credential" "application" {
  name                      = "vinasign-serveur"
  user_assigned_identity_id = azurerm_user_assigned_identity.application.id
  audience                  = ["api://AzureADTokenExchange"]
  issuer                    = azurerm_kubernetes_cluster.principal.oidc_issuer_url
  subject                   = "system:serviceaccount:vinasign:vinasign-serveur"
}
