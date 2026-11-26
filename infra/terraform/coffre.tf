# Key Vault Premium : clés de cachet et de jetons en HSM, non exportables (VS-031) ; secrets de connexion.
# Refus réseau par défaut ; accès par Workload Identity depuis AKS, jamais par secret dans une image.
resource "azurerm_key_vault" "principal" {
  name                          = "${local.nom_compact}kv"
  location                      = azurerm_resource_group.principal.location
  resource_group_name           = azurerm_resource_group.principal.name
  tenant_id                     = data.azurerm_client_config.courant.tenant_id
  sku_name                      = "premium"
  purge_protection_enabled      = true
  soft_delete_retention_days    = 90
  rbac_authorization_enabled    = true
  public_network_access_enabled = true
  tags                          = local.etiquettes

  network_acls {
    default_action             = "Deny"
    bypass                     = "AzureServices"
    ip_rules                   = var.ip_administration
    virtual_network_subnet_ids = [azurerm_subnet.aks.id]
  }
}

resource "azurerm_role_assignment" "coffre_exploitant" {
  scope                = azurerm_key_vault.principal.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.courant.object_id
}

# L'application lit les secrets et signe avec les clés ; elle ne peut ni exporter ni supprimer une clé.
resource "azurerm_role_assignment" "coffre_application_secrets" {
  scope                = azurerm_key_vault.principal.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.application.principal_id
}

resource "azurerm_role_assignment" "coffre_application_cles" {
  scope                = azurerm_key_vault.principal.id
  role_definition_name = "Key Vault Crypto User"
  principal_id         = azurerm_user_assigned_identity.application.principal_id
}

resource "azurerm_key_vault_secret" "base_url" {
  name            = "vinasign-db-url"
  key_vault_id    = azurerm_key_vault.principal.id
  value           = "jdbc:postgresql://${azurerm_postgresql_flexible_server.principal.fqdn}:5432/${var.base_nom}?sslmode=require"
  content_type    = "text/plain"
  expiration_date = timeadd(plantimestamp(), "8760h")
  depends_on      = [azurerm_role_assignment.coffre_exploitant]

  lifecycle {
    ignore_changes = [expiration_date]
  }
}

resource "azurerm_key_vault_secret" "base_mdp" {
  name            = "vinasign-db-mdp"
  key_vault_id    = azurerm_key_vault.principal.id
  value           = random_password.base.result
  content_type    = "text/plain"
  expiration_date = timeadd(plantimestamp(), "8760h")
  depends_on      = [azurerm_role_assignment.coffre_exploitant]

  lifecycle {
    ignore_changes = [expiration_date]
  }
}
