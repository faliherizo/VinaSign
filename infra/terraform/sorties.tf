output "groupe_ressources" {
  value = azurerm_resource_group.principal.name
}

output "aks_nom" {
  value = azurerm_kubernetes_cluster.principal.name
}

output "aks_emetteur_oidc" {
  value = azurerm_kubernetes_cluster.principal.oidc_issuer_url
}

output "registre" {
  value = azurerm_container_registry.principal.login_server
}

output "coffre_uri" {
  value = azurerm_key_vault.principal.vault_uri
}

output "identite_application_client_id" {
  description = "À poser sur le compte de service du chart Helm (azure.workload.identity/client-id)."
  value       = azurerm_user_assigned_identity.application.client_id
}

output "stockage_compte" {
  value = azurerm_storage_account.principal.name
}

output "api_point_entree" {
  value = azurerm_cdn_frontdoor_endpoint.api.host_name
}
