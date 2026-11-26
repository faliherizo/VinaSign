# language: fr
@VINASIGN
Fonctionnalité: Socle — page d'état et objectifs de service (VS-124)
  L'état du service, l'historique des incidents et les objectifs publiés sont consultables sans compte. Les composants,
  les impacts, les phases et les objectifs sont des référentiels : rien n'est écrit dans l'interface.

  @VS-124
  Scénario: L'état du service se consulte sans authentification
    Étant donné que j'agis en tant que "visiteur"
    Quand j'exécute le cas "etat.public"
    Alors la plateforme se comporte comme attendu

  @VS-124
  Scénario: La page d'état reste accessible à toutes les largeurs
    Quand j'ouvre l'écran "Portail — état du service"
    Alors l'écran est accessible à 390, 768, 1280 et 1680 px de large
