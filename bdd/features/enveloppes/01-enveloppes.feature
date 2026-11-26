# language: fr
@VINASIGN
Fonctionnalité: Enveloppes et documents
  Une application crée une enveloppe, désigne ses signataires dans un ordre, dépose le PDF par une URL présignée,
  puis l'envoie. Chaque transition est une action ; aucun statut ne se saisit.

  @VS-020 @VS-021 @VS-022 @VS-023 @VS-024
  Scénario: Parcours complet d'une enveloppe en mode document
    Quand j'exécute le cas "enveloppes.parcours-document"
    Alors la plateforme se comporte comme attendu

  @VS-021 @VS-024 @VS-025
  Scénario: Refus explicites et annulation
    Quand j'exécute le cas "enveloppes.refus-et-annulation"
    Alors la plateforme se comporte comme attendu

  @VS-064
  Scénario: Liste paginée des enveloppes, filtrée par statut
    Quand j'exécute le cas "enveloppes.liste"
    Alors la plateforme se comporte comme attendu
