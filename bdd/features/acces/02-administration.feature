# language: fr
@VINASIGN
Fonctionnalité: Accès — organisations, utilisateurs du portail et applications
  L'administration de la plateforme crée les organisations ; leurs administrateurs, authentifiés par le fournisseur
  d'identité avec MFA, gèrent leurs utilisateurs et leurs applications clientes.

  @VS-010 @VS-014
  Scénario: Organisation, administrateur invité, rôles et MFA, suspension et réactivation
    Quand j'exécute le cas "organisations.cycle"
    Alors la plateforme se comporte comme attendu

  @VS-011 @VS-013
  Scénario: Application cliente enregistrée, rotation de ses clés et révocation immédiate
    Quand j'exécute le cas "applications.cycle"
    Alors la plateforme se comporte comme attendu

  @VS-122
  Scénario: Les étapes d'une inscription en libre-service exigent le jeton reçu par courriel
    Étant donné que j'agis en tant que "visiteur"
    Quand j'exécute le cas "organisation.inscription"
    Alors la plateforme se comporte comme attendu

  @VS-122
  Scénario: Le formulaire d'inscription est accessible à toutes les largeurs
    Quand j'ouvre l'écran "Portail — inscription"
    Alors l'écran est accessible à 390, 768, 1280 et 1680 px de large

  @VS-014 @VS-121
  Scénario: L'écran de connexion du portail est accessible à toutes les largeurs
    Quand j'ouvre l'écran "Portail — connexion"
    Alors l'écran est accessible à 390, 768, 1280 et 1680 px de large

  @VS-014 @VS-121
  Scénario: Un compte sans le rôle requis est informé à l'écran de connexion, accessible à toutes les largeurs
    Quand j'ouvre l'écran "Portail — connexion (rôle manquant)"
    Alors je vois "n'a pas le rôle requis"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large

  @VS-121 @VS-053
  Scénario: L'administrateur de l'organisation consulte sa facturation, accessible à toutes les largeurs
    Quand j'exécute le cas "portail.preparateur"
    Alors la plateforme se comporte comme attendu
    Quand je me connecte au portail avec MFA en tant que "admin-sig-${unique}@exemple.ca"
    Et je clique sur "Facturation"
    Alors je vois "Facturation"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
