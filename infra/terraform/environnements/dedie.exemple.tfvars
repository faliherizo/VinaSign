# Instance dédiée (VS-134) : copier en dedie-<client>-<environnement>.tfvars ; état Terraform propre (backend : key = dedie-<client>-<environnement>.tfstate).
# Même code et mêmes garde-fous que la production partagée ; seules les tailles et la plage réseau changent.
client_dedie                 = "<code-client>"
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
