# language: fr
@VINASIGN
Fonctionnalité: Signataire — lire, remplir et signer à l'écran
  Le signataire ouvre le lien reçu, consent à signer électroniquement, lit le document, remplit ses champs et signe en
  dessinant ou en tapant son nom. Chaque état de l'écran est accessible, sans débordement ni superposition, du téléphone
  au grand écran.

  @VS-040 @VS-050 @VS-051 @VS-052 @VS-053 @VS-054 @VS-070 @VS-071 @VS-072 @VS-073
  Scénario: Du lien reçu par courriel à la signature au clavier, accessible à chaque étape
    Quand j'exécute le cas "signataire.parcours-ecran"
    Et j'ouvre l'écran "Signataire — parcours (téléphone)"
    Alors je vois "Consentement à la signature électronique"
    Et je vois "Lecture et signature"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
    Quand je coche "J'ai lu et j'accepte"
    Et je clique sur "Continuer"
    Alors je vois "Document à signer"
    Et je vois "Page 1 / 1"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
    Quand je clique sur "Signer le document"
    Alors je vois "Des champs obligatoires sont vides"
    Et l'écran est accessible, sans débordement ni superposition
    Quand je clique sur "Refuser de signer"
    Alors je vois "Le refus met fin à l'enveloppe"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
    Quand je clique sur "Revenir au document"
    Alors je vois "Document à signer"
    Quand je saisis "Directrice" dans le champ "Titre du poste"
    Et je clique sur "Taper mon nom"
    Et je saisis "Camille Gagnon" dans le champ "Votre nom complet"
    Et je clique sur "Signer le document"
    Alors je vois "Merci, votre signature est enregistrée."
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
    Quand j'exécute le cas "signataire.parcours-ecran-verification"
    Alors la plateforme se comporte comme attendu
    Quand j'ouvre l'écran "Signataire — parcours"
    Alors je vois "Ce lien a déjà servi"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large


  @VS-042 @VS-043 @VS-053
  Scénario: La vérification d'identité par code est accessible, du choix du moyen à la saisie du code
    Quand j'exécute le cas "signataire.parcours-authentification"
    Alors la plateforme se comporte comme attendu
    Quand j'ouvre l'écran "Signataire — parcours"
    Et je coche "J'ai lu et j'accepte"
    Et je clique sur "Continuer"
    Alors je vois "Vérifier votre identité"
    Et je vois "Vérification"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
    Quand je clique sur "Recevoir un code"
    Alors je vois "Code reçu"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
