locals {
  # Instance dédiée (VS-134) : le code du client s'insère dans chaque nom ; l'instance partagée garde ses noms.
  nom = var.client_dedie == null ? "${var.projet}-${var.environnement}" : "${var.projet}-${var.client_dedie}-${var.environnement}"
  # Noms sans tiret (coffre, stockage, registre) : 24 caractères au plus. Pour une instance dédiée, l'environnement est
  # abrégé, sinon la troncature confondrait la préproduction et la production d'un client au code long.
  environnement_court = { integration = "int", preproduction = "pre", production = "prd", "bac-a-sable" = "bas" }
  nom_compact         = var.client_dedie == null ? substr(replace(local.nom, "-", ""), 0, 20) : substr("${var.projet}${var.client_dedie}${local.environnement_court[var.environnement]}", 0, 20)

  etiquettes = merge({
    projet        = var.projet
    environnement = var.environnement
    gere_par      = "terraform"
    residence     = "canada"
    instance      = coalesce(var.client_dedie, "partagee")
  }, var.etiquettes)
}

data "azurerm_client_config" "courant" {}
