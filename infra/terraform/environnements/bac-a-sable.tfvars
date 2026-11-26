# Bac à sable client : une production isolée (aucune ressource partagée), avec des certificats de test (VS-095).
environnement                = "bac-a-sable"
region                       = "canadacentral"
zones                        = ["1", "2"]
plage_reseau                 = "10.63.0.0/16"
version_kubernetes           = "1.31"
taille_noeud                 = "Standard_D2s_v5"
noeuds_min                   = 2
noeuds_max                   = 4
postgres_sku                 = "GP_Standard_D2s_v3"
postgres_stockage_mo         = 32768
postgres_haute_disponibilite = false
retention_sauvegardes_jours  = 7
retention_preuve_jours       = 30
