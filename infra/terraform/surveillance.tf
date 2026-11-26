# Journaux, métriques et traces (VS-004) : l'application exporte en OpenTelemetry vers Application Insights.
resource "azurerm_log_analytics_workspace" "principal" {
  name                = "${local.nom}-log"
  location            = azurerm_resource_group.principal.location
  resource_group_name = azurerm_resource_group.principal.name
  sku                 = "PerGB2018"
  retention_in_days   = 90
  tags                = local.etiquettes
}

resource "azurerm_application_insights" "principal" {
  name                = "${local.nom}-appi"
  location            = azurerm_resource_group.principal.location
  resource_group_name = azurerm_resource_group.principal.name
  workspace_id        = azurerm_log_analytics_workspace.principal.id
  application_type    = "java"
  tags                = local.etiquettes
}

resource "azurerm_monitor_action_group" "exploitation" {
  name                = "${local.nom}-exploitation"
  resource_group_name = azurerm_resource_group.principal.name
  short_name          = "vinasign"
  email_receiver {
    name          = "exploitation"
    email_address = var.courriel_alertes
  }
  tags = local.etiquettes
}

resource "azurerm_key_vault_secret" "telemetrie" {
  name            = "vinasign-otel-connexion"
  key_vault_id    = azurerm_key_vault.principal.id
  value           = azurerm_application_insights.principal.connection_string
  content_type    = "text/plain"
  expiration_date = timeadd(plantimestamp(), "8760h")
  depends_on      = [azurerm_role_assignment.coffre_exploitant]

  lifecycle {
    ignore_changes = [expiration_date]
  }
}

# Sondes synthétiques (VS-124) : chaque composant est sondé de l'extérieur, depuis plusieurs emplacements, par le
# même chemin que les clients (Front Door). La disponibilité mesurée alimente le SLO publié (référentiel objectif-service)
# et déclenche une alerte à l'exploitation, qui publie alors l'incident sur la page d'état.
locals {
  sondes = {
    sante        = "/actuator/health"
    etat         = "/api/v1/availability"
    referentiels = "/api/v1/referentiels/langue"
  }
  # Emplacements nord-américains des tests de disponibilité (Azure n'en propose pas au Canada) : Virginie, Chicago, San José.
  emplacements_sondes = ["us-va-ash-azr", "us-il-ch1-azr", "us-ca-sjc-azr"]
}

resource "azurerm_application_insights_standard_web_test" "sonde" {
  for_each                = local.sondes
  name                    = "${local.nom}-sonde-${each.key}"
  resource_group_name     = azurerm_resource_group.principal.name
  location                = azurerm_resource_group.principal.location
  application_insights_id = azurerm_application_insights.principal.id
  geo_locations           = local.emplacements_sondes
  frequency               = 300
  timeout                 = 30
  enabled                 = true
  retry_enabled           = true
  request {
    url = "https://${var.nom_hote_api}${each.value}"
  }
  validation_rules {
    expected_status_code        = 200
    ssl_check_enabled           = true
    ssl_cert_remaining_lifetime = 14
  }
  tags = local.etiquettes
}

resource "azurerm_monitor_metric_alert" "sonde" {
  for_each            = azurerm_application_insights_standard_web_test.sonde
  name                = "${local.nom}-indisponible-${each.key}"
  resource_group_name = azurerm_resource_group.principal.name
  scopes              = [each.value.id, azurerm_application_insights.principal.id]
  description         = "Sonde ${each.key} en échec depuis au moins deux emplacements : publier un incident (VS-124)."
  severity            = 1
  frequency           = "PT1M"
  window_size         = "PT5M"
  application_insights_web_test_location_availability_criteria {
    web_test_id           = each.value.id
    component_id          = azurerm_application_insights.principal.id
    failed_location_count = 2
  }
  action {
    action_group_id = azurerm_monitor_action_group.exploitation.id
  }
  tags = local.etiquettes
}

# Renouvellement des horodatages d'archive en échec (VS-132) : compteur vinasign.archivage.echecs exporté en OpenTelemetry.
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "archivage" {
  name                 = "${local.nom}-archivage-echec"
  resource_group_name  = azurerm_resource_group.principal.name
  location             = azurerm_resource_group.principal.location
  scopes               = [azurerm_application_insights.principal.id]
  description          = "Un renouvellement d'horodatage d'archive a échoué (VS-132) ; la tâche réessaie, l'échéance approche."
  severity             = 2
  evaluation_frequency = "PT1H"
  window_duration      = "PT6H"
  criteria {
    query                   = "customMetrics | where name == 'vinasign.archivage.echecs' | summarize total = sum(valueSum)"
    time_aggregation_method = "Total"
    metric_measure_column   = "total"
    operator                = "GreaterThan"
    threshold               = 0
  }
  action {
    action_groups = [azurerm_monitor_action_group.exploitation.id]
  }
  tags = local.etiquettes
}
