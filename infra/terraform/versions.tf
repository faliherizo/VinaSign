terraform {
  required_version = ">= 1.7"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.3"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # État distant : un conteneur par environnement, fourni à l'initialisation
  # (terraform init -backend-config=environnements/<env>.backend.hcl). Aucun nom de compte n'est écrit ici.
  backend "azurerm" {}
}
