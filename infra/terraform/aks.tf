# AKS : pool système et pool applicatif répartis sur les zones ; Workload Identity ; politique réseau Cilium.
resource "azurerm_kubernetes_cluster" "principal" {
  name                              = "${local.nom}-aks"
  location                          = azurerm_resource_group.principal.location
  resource_group_name               = azurerm_resource_group.principal.name
  dns_prefix                        = local.nom
  kubernetes_version                = var.version_kubernetes
  sku_tier                          = var.environnement == "production" ? "Standard" : "Free"
  oidc_issuer_enabled               = true
  workload_identity_enabled         = true
  role_based_access_control_enabled = true
  local_account_disabled            = true
  azure_policy_enabled              = true

  api_server_access_profile {
    authorized_ip_ranges = var.ip_administration
  }

  azure_active_directory_role_based_access_control {
    azure_rbac_enabled = true
    tenant_id          = data.azurerm_client_config.courant.tenant_id
  }

  default_node_pool {
    name                         = "systeme"
    vm_size                      = "Standard_D2s_v5"
    zones                        = length(var.zones) > 0 ? var.zones : null
    auto_scaling_enabled         = true
    min_count                    = length(var.zones) > 0 ? length(var.zones) : 1
    max_count                    = 5
    vnet_subnet_id               = azurerm_subnet.aks.id
    only_critical_addons_enabled = true
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin     = "azure"
    network_policy     = "cilium"
    network_data_plane = "cilium"
    load_balancer_sku  = "standard"
  }

  oms_agent {
    log_analytics_workspace_id = azurerm_log_analytics_workspace.principal.id
  }

  tags = local.etiquettes
}

resource "azurerm_kubernetes_cluster_node_pool" "application" {
  name                  = "app"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.principal.id
  vm_size               = var.taille_noeud
  zones                 = length(var.zones) > 0 ? var.zones : null
  auto_scaling_enabled  = true
  min_count             = var.noeuds_min
  max_count             = var.noeuds_max
  vnet_subnet_id        = azurerm_subnet.aks.id
  node_labels           = { charge = "application" }
  tags                  = local.etiquettes
}

resource "azurerm_role_assignment" "aks_registre" {
  scope                = azurerm_container_registry.principal.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_kubernetes_cluster.principal.kubelet_identity[0].object_id
}
