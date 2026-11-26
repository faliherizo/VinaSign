# language: fr
@VINASIGN
Fonctionnalité: Signataire — signature intégrée dans la page de l'application
  L'application cliente affiche la signature dans sa propre page, sans renvoyer l'utilisateur ailleurs. Seules les
  origines qu'elle a déclarées peuvent l'afficher ; elle est avisée de l'issue, sans aucune donnée personnelle.

  @VS-055 @VS-046
  Scénario: L'application intègre la session, l'utilisateur signe, l'application est avisée
    Quand j'exécute le cas "signataire.integration-ecran"
    Et l'application affiche la session intégrée dans sa page
    Et le signataire consent et signe en tapant son nom dans la page de l'application
    Alors l'application reçoit les avis "ready, signed"
