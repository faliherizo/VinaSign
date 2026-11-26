# language: fr
@VINASIGN
Fonctionnalité: Socle — listes dynamiques (règle « tout est dynamique »)
  Toute liste de la plateforme vient d'un référentiel administrable, servi par l'API et traduit. Aucune option n'est
  écrite dans le code : une valeur ajoutée en base apparaît sans redéploiement.

  @VS-R01
  Scénario: Chaque référentiel annoncé par l'API se consulte dans la langue demandée
    Étant donné que j'agis en tant que "visiteur"
    Quand j'exécute le cas "socle.referentiels"
    Alors la plateforme se comporte comme attendu

  @VS-R01
  Scénario: Le portail tire son choix de langue du référentiel et change de langue sans rechargement
    Quand j'ouvre l'écran "Portail — référentiels"
    Alors l'écran est accessible, sans débordement ni superposition
    Quand je choisis "Anglais" dans la liste "Langue"
    Alors je vois "Reference lists"
    Et l'écran est accessible, sans débordement ni superposition

  @VS-R01
  Scénario: Le portail et l'écran du signataire restent accessibles à toutes les largeurs
    Quand j'ouvre l'écran "Portail — référentiels (téléphone)"
    Alors l'écran est accessible, sans débordement ni superposition
    Quand j'ouvre l'écran "Portail — référentiels (tablette)"
    Alors l'écran est accessible, sans débordement ni superposition
    Quand j'ouvre l'écran "Portail — référentiels (grand écran)"
    Alors l'écran est accessible, sans débordement ni superposition
    Quand j'ouvre l'écran "Signataire — accueil (téléphone)"
    Alors l'écran est accessible, sans débordement ni superposition
