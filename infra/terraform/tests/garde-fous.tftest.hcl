# Garde-fous de l'infrastructure (VS-003), éprouvés sans compte Azure grâce aux fournisseurs simulés.
mock_provider "azurerm" {
  override_during = plan
  mock_data "azurerm_client_config" {
    defaults = {
      tenant_id = "00000000-0000-0000-0000-000000000000"
      object_id = "00000000-0000-0000-0000-000000000000"
    }
  }
  mock_resource "azurerm_storage_container" {
    defaults = {
      id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg/providers/Microsoft.Storage/storageAccounts/st/blobServices/default/containers/preuve"
    }
  }
  mock_resource "azurerm_user_assigned_identity" {
    defaults = {
      id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/app"
    }
  }
  mock_resource "azurerm_postgresql_flexible_server" {
    defaults = {
      id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg/providers/Microsoft.DBforPostgreSQL/flexibleServers/pg"
    }
  }
  mock_resource "azurerm_key_vault" {
    defaults = {
      id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/rg/providers/Microsoft.KeyVault/vaults/kv"
    }
  }
}
mock_provider "random" {}

variables {
  nom_hote_api      = "api.exemple.test"
  ip_administration = ["203.0.113.5/32"]
  courriel_alertes  = "exploitation@exemple.test"
}

run "production" {
  command = plan
  # L'attribution AcrPull lit l'identité kubelet, que seul Azure calcule : elle est hors du plan simulé.
  plan_options {
    target = [
      azurerm_key_vault.principal,
      azurerm_postgresql_flexible_server.reprise,
      azurerm_storage_container_immutability_policy.preuve,
      azurerm_kubernetes_cluster.principal,
      azurerm_key_vault_secret.base_url,
    ]
  }
  variables {
    environnement                = "production"
    region                       = "canadacentral"
    region_reprise               = "canadaeast"
    zones                        = ["1", "2", "3"]
    plage_reseau                 = "10.62.0.0/16"
    version_kubernetes           = "1.31"
    taille_noeud                 = "Standard_D4s_v5"
    noeuds_min                   = 3
    noeuds_max                   = 9
    postgres_sku                 = "GP_Standard_D4s_v3"
    postgres_stockage_mo         = 131072
    postgres_haute_disponibilite = true
    retention_sauvegardes_jours  = 35
    retention_preuve_jours       = 3650
  }

  assert {
    condition     = azurerm_key_vault.principal.sku_name == "premium"
    error_message = "Les clés de cachet exigent un coffre adossé à un HSM (premium)."
  }
  assert {
    condition     = azurerm_key_vault.principal.network_acls[0].default_action == "Deny"
    error_message = "Le coffre refuse le réseau par défaut."
  }
  assert {
    condition     = azurerm_postgresql_flexible_server.principal.public_network_access_enabled == false
    error_message = "La base n'a aucune adresse publique."
  }
  assert {
    condition     = azurerm_storage_container_immutability_policy.preuve.locked == true
    error_message = "En production, l'immuabilité de la preuve est verrouillée."
  }
  assert {
    condition     = azurerm_storage_account.principal.shared_access_key_enabled == false
    error_message = "Aucune clé partagée du stockage : accès par identité seulement."
  }
  assert {
    condition     = length(azurerm_postgresql_flexible_server.reprise) == 1 && azurerm_postgresql_flexible_server.reprise[0].location == "canadaeast"
    error_message = "La production a une réplique de reprise dans Canada East."
  }
  assert {
    condition     = azurerm_kubernetes_cluster.principal.local_account_disabled == true
    error_message = "Aucun compte local sur AKS : accès par Entra ID et RBAC."
  }
}

run "integration_sans_reprise" {
  command = plan
  # L'attribution AcrPull lit l'identité kubelet, que seul Azure calcule : elle est hors du plan simulé.
  plan_options {
    target = [
      azurerm_key_vault.principal,
      azurerm_postgresql_flexible_server.reprise,
      azurerm_storage_container_immutability_policy.preuve,
      azurerm_kubernetes_cluster.principal,
      azurerm_key_vault_secret.base_url,
    ]
  }
  variables {
    environnement                = "integration"
    region                       = "canadacentral"
    zones                        = ["1"]
    plage_reseau                 = "10.60.0.0/16"
    version_kubernetes           = "1.31"
    taille_noeud                 = "Standard_D2s_v5"
    noeuds_min                   = 1
    noeuds_max                   = 3
    postgres_sku                 = "B_Standard_B2s"
    postgres_stockage_mo         = 32768
    postgres_haute_disponibilite = false
    retention_sauvegardes_jours  = 7
    retention_preuve_jours       = 1
  }

  assert {
    condition     = length(azurerm_postgresql_flexible_server.reprise) == 0
    error_message = "Sans région de reprise, aucune réplique."
  }
  assert {
    condition     = azurerm_storage_container_immutability_policy.preuve.locked == false
    error_message = "Hors production, l'immuabilité reste déverrouillable (environnement jetable)."
  }
}

run "region_hors_canada_refusee" {
  command = plan
  variables {
    environnement                = "integration"
    region                       = "eastus"
    zones                        = []
    plage_reseau                 = "10.60.0.0/16"
    version_kubernetes           = "1.31"
    taille_noeud                 = "Standard_D2s_v5"
    noeuds_min                   = 1
    noeuds_max                   = 3
    postgres_sku                 = "B_Standard_B2s"
    postgres_stockage_mo         = 32768
    postgres_haute_disponibilite = false
    retention_sauvegardes_jours  = 7
    retention_preuve_jours       = 1
  }
  expect_failures = [var.region]
}

run "administration_ouverte_refusee" {
  command = plan
  variables {
    environnement                = "integration"
    region                       = "canadacentral"
    zones                        = []
    plage_reseau                 = "10.60.0.0/16"
    version_kubernetes           = "1.31"
    taille_noeud                 = "Standard_D2s_v5"
    noeuds_min                   = 1
    noeuds_max                   = 3
    postgres_sku                 = "B_Standard_B2s"
    postgres_stockage_mo         = 32768
    postgres_haute_disponibilite = false
    retention_sauvegardes_jours  = 7
    retention_preuve_jours       = 1
    ip_administration            = ["0.0.0.0/0"]
  }
  expect_failures = [var.ip_administration]
}

# Instance dédiée (VS-134) : mêmes garde-fous que la production partagée, noms distincts, étiquetée au nom du client.
run "instance_dediee" {
  command = plan
  plan_options {
    target = [
      azurerm_key_vault.principal,
      azurerm_storage_account.principal,
      azurerm_storage_container_immutability_policy.preuve,
      azurerm_postgresql_flexible_server.principal,
    ]
  }
  variables {
    client_dedie                 = "cisssca"
    environnement                = "production"
    region                       = "canadacentral"
    region_reprise               = "canadaeast"
    zones                        = ["1", "2", "3"]
    plage_reseau                 = "10.70.0.0/16"
    version_kubernetes           = "1.31"
    taille_noeud                 = "Standard_D4s_v5"
    noeuds_min                   = 2
    noeuds_max                   = 6
    postgres_sku                 = "GP_Standard_D2s_v3"
    postgres_stockage_mo         = 65536
    postgres_haute_disponibilite = true
    retention_sauvegardes_jours  = 35
    retention_preuve_jours       = 3650
  }

  assert {
    condition     = azurerm_key_vault.principal.name == "vinasigncissscaprdkv"
    error_message = "Coffre propre au client : ${azurerm_key_vault.principal.name}."
  }
  assert {
    condition     = azurerm_storage_account.principal.name == "vinasigncissscaprdst" && azurerm_postgresql_flexible_server.principal.name == "vinasign-cisssca-production-pg"
    error_message = "Stockage et base propres au client."
  }
  assert {
    condition     = azurerm_storage_account.principal.tags["instance"] == "cisssca"
    error_message = "Ressources étiquetées au nom du client (facturation, inventaire)."
  }
  assert {
    condition     = azurerm_key_vault.principal.sku_name == "premium" && azurerm_storage_container_immutability_policy.preuve.locked == true
    error_message = "Mêmes garde-fous que la production partagée."
  }
}

run "instance_dediee_hors_production_refusee" {
  command = plan
  variables {
    client_dedie                 = "cisssca"
    environnement                = "integration"
    region                       = "canadacentral"
    zones                        = ["1"]
    plage_reseau                 = "10.71.0.0/16"
    version_kubernetes           = "1.31"
    taille_noeud                 = "Standard_D2s_v5"
    noeuds_min                   = 1
    noeuds_max                   = 3
    postgres_sku                 = "B_Standard_B2s"
    postgres_stockage_mo         = 32768
    postgres_haute_disponibilite = false
    retention_sauvegardes_jours  = 7
    retention_preuve_jours       = 1
  }
  expect_failures = [var.client_dedie]
}
