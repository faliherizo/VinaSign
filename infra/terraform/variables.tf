# Aucune valeur propre à un environnement n'a de défaut : elle vient de environnements/<env>.tfvars.

variable "projet" {
  description = "Préfixe des ressources."
  type        = string
  default     = "vinasign"
}

variable "environnement" {
  description = "Environnement déployé (dossier d'architecture § 9). Le bac à sable client est une production isolée."
  type        = string
  validation {
    condition     = contains(["integration", "preproduction", "production", "bac-a-sable"], var.environnement)
    error_message = "Environnement inconnu : integration, preproduction, production ou bac-a-sable."
  }
}

variable "region" {
  description = "Région principale. Résidence des données au Canada (VS-112)."
  type        = string
  validation {
    condition     = contains(["canadacentral", "canadaeast"], var.region)
    error_message = "Résidence des données au Canada : canadacentral ou canadaeast seulement."
  }
}

variable "region_reprise" {
  description = "Région de reprise (réplique en lecture de la base) ; null pour ne pas en créer."
  type        = string
  default     = null
  validation {
    condition     = var.region_reprise == null || contains(["canadacentral", "canadaeast"], coalesce(var.region_reprise, "x"))
    error_message = "La reprise reste au Canada : canadacentral ou canadaeast."
  }
}

variable "zones" {
  description = "Zones de disponibilité ; liste vide pour une région sans zones."
  type        = list(string)
}

variable "plage_reseau" {
  description = "Plage d'adresses du réseau virtuel (CIDR /16), distincte par environnement."
  type        = string
  validation {
    condition     = can(cidrhost(var.plage_reseau, 0)) && endswith(var.plage_reseau, "/16")
    error_message = "Plage CIDR /16 attendue."
  }
}

variable "version_kubernetes" {
  type = string
}

variable "taille_noeud" {
  type = string
}

variable "noeuds_min" {
  type = number
}

variable "noeuds_max" {
  type = number
}

variable "postgres_version" {
  type    = string
  default = "16"
}

variable "postgres_sku" {
  type = string
}

variable "postgres_stockage_mo" {
  type = number
}

variable "postgres_haute_disponibilite" {
  description = "Veille synchrone (dossier d'architecture § 9) ; requise en production."
  type        = bool
}

variable "retention_sauvegardes_jours" {
  description = "Rétention des sauvegardes PostgreSQL (RPO 15 min par journaux, restauration à un instant)."
  type        = number
  validation {
    condition     = var.retention_sauvegardes_jours >= 7 && var.retention_sauvegardes_jours <= 35
    error_message = "Rétention entre 7 et 35 jours (limite de PostgreSQL Flexible Server)."
  }
}

variable "base_nom" {
  type    = string
  default = "vinasign"
}

variable "retention_preuve_jours" {
  description = "Immuabilité du conteneur de preuve (WORM) ; une durée de conservation décidée à l'EFVP (VS-112)."
  type        = number
  validation {
    condition     = var.retention_preuve_jours >= 1
    error_message = "La preuve est immuable au moins un jour."
  }
}

variable "nom_hote_api" {
  description = "Nom d'hôte public de l'API servi par Front Door."
  type        = string
}

variable "ip_administration" {
  description = "CIDR d'administration seuls admis sur le plan de contrôle AKS et le coffre (exécutant Terraform, poste d'exploitation)."
  type        = list(string)
  validation {
    condition     = length(var.ip_administration) > 0 && !contains(var.ip_administration, "0.0.0.0/0")
    error_message = "Déclarer au moins une adresse d'administration, et jamais 0.0.0.0/0."
  }
}

variable "courriel_alertes" {
  description = "Destinataire des alertes d'exploitation ; fourni par l'environnement d'exécution (TF_VAR_courriel_alertes)."
  type        = string
  sensitive   = true
}

variable "etiquettes" {
  type    = map(string)
  default = {}
}

variable "client_dedie" {
  description = "Instance dédiée à un client (VS-134) : son code court ; null pour l'instance partagée. Même code, déploiement isolé (abonnement, réseau, base, coffre, stockage propres)."
  type        = string
  default     = null
  validation {
    condition     = var.client_dedie == null || can(regex("^[a-z][a-z0-9]{1,11}$", coalesce(var.client_dedie, "x")))
    error_message = "Code du client : 2 à 12 caractères, lettres minuscules et chiffres, commençant par une lettre."
  }
  validation {
    condition     = var.client_dedie == null || contains(["preproduction", "production"], var.environnement)
    error_message = "Une instance dédiée n'existe qu'en préproduction ou en production."
  }
}
