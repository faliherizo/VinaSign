# Copier en <environnement>.backend.hcl (non versionné) : un état par environnement, chiffré, au Canada.
resource_group_name  = "<groupe de l'état Terraform>"
storage_account_name = "<compte de stockage de l'état>"
container_name       = "tfstate"
key                  = "<environnement>.tfstate"
use_azuread_auth     = true
