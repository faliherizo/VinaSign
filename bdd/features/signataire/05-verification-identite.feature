# language: fr
@VINASIGN
Fonctionnalité: Signataire — vérification d'identité par un fournisseur (VS-131)
  Une organisation exige une identité vérifiée : le signataire est envoyé chez le fournisseur choisi par configuration,
  dont le rappel signé élève la session. Le résultat (fournisseur, méthode, assurance) est consigné dans la preuve.

  @VS-131
  Scénario: Un rappel non signé par le fournisseur est refusé
    Étant donné que j'agis en tant que "visiteur"
    Quand j'exécute le cas "identite.rappel"
    Alors la plateforme se comporte comme attendu
