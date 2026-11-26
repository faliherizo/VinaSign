# Stockage objet privé : documents (versions immuables, VS-022) et preuve (WORM, VS-071).
resource "azurerm_storage_account" "principal" {
  name                            = "${local.nom_compact}st"
  resource_group_name             = azurerm_resource_group.principal.name
  location                        = azurerm_resource_group.principal.location
  account_tier                    = "Standard"
  account_replication_type        = var.region_reprise != null ? "GZRS" : "ZRS"
  min_tls_version                 = "TLS1_2"
  https_traffic_only_enabled      = true
  allow_nested_items_to_be_public = false
  shared_access_key_enabled       = false
  public_network_access_enabled   = true
  tags                            = local.etiquettes

  blob_properties {
    versioning_enabled = true
    delete_retention_policy {
      days = 30
    }
    container_delete_retention_policy {
      days = 30
    }
  }

  network_rules {
    default_action             = "Deny"
    bypass                     = ["AzureServices"]
    ip_rules                   = [for ip in var.ip_administration : trimsuffix(ip, "/32")]
    virtual_network_subnet_ids = [azurerm_subnet.aks.id]
  }
}

resource "azurerm_storage_container" "documents" {
  name                  = "documents"
  storage_account_id    = azurerm_storage_account.principal.id
  container_access_type = "private"
}

resource "azurerm_storage_container" "preuve" {
  name                  = "preuve"
  storage_account_id    = azurerm_storage_account.principal.id
  container_access_type = "private"
}

# Immuabilité : une écriture dans le conteneur de preuve ne peut être ni modifiée ni supprimée avant l'échéance.
resource "azurerm_storage_container_immutability_policy" "preuve" {
  storage_container_resource_manager_id = azurerm_storage_container.preuve.id
  immutability_period_in_days           = var.retention_preuve_jours
  protected_append_writes_all_enabled   = true
  locked                                = var.environnement == "production"
}

resource "azurerm_role_assignment" "stockage_application" {
  scope                = azurerm_storage_account.principal.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_user_assigned_identity.application.principal_id
}
