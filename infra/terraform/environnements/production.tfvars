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
# Durée provisoire : la durée de conservation de la preuve est arrêtée à l'EFVP (VS-112).
retention_preuve_jours = 3650
