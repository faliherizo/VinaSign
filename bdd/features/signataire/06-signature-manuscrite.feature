# language: fr
@VINASIGN
Fonctionnalité: Signataire — signer un document à la main, scellé et horodaté
  Chaque signataire, à son tour, ouvre le lien reçu, consent, remplit ses champs (date, case à cocher) et signe en
  dessinant sa signature au doigt, au stylet ou à la souris ; ses initiales sont apposées par le même geste. Une fois
  toutes les signatures recueillies, la plateforme scelle le document final et le fait horodater par une autorité
  (RFC 3161). N'importe qui vérifie ce document sans compte, et la moindre altération est détectée.

  @VS-023 @VS-040 @VS-041 @VS-051 @VS-052 @VS-033 @VS-036 @VS-070 @VS-072 @VS-073
  Scénario: Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement
    Quand j'exécute le cas "signature.manuscrite-preparation"
    Alors la plateforme se comporte comme attendu
    Quand j'ouvre l'écran "Signataire — signature manuscrite"
    Et je coche "J'ai lu et j'accepte"
    Et je clique sur "Continuer"
    Alors je vois "Document à signer"
    Quand je saisis "${aujourdhui}" dans le champ "Date de signature"
    Et je coche "J'approuve les conditions"
    Et je clique sur "Dessiner"
    Et je clique sur "Signer le document"
    Alors je vois "Dessinez votre signature ou tapez votre nom avant de signer."
    Quand je dessine ma signature dans le "Cadre de signature"
    Alors l'écran est accessible, sans débordement ni superposition
    Quand je clique sur "Signer le document"
    Alors je vois "Merci, votre signature est enregistrée."
    Quand j'exécute le cas "signature.manuscrite-tour-suivant"
    Alors la plateforme se comporte comme attendu
    Quand j'ouvre l'écran "Signataire — signature manuscrite"
    Et je coche "J'ai lu et j'accepte"
    Et je clique sur "Continuer"
    Et je clique sur "Dessiner"
    Et je dessine ma signature dans le "Cadre de signature"
    Et je clique sur "Signer le document"
    Alors je vois "Merci, votre signature est enregistrée."
    Quand j'exécute le cas "signature.manuscrite-verification"
    Alors la plateforme se comporte comme attendu

  @VS-064 @VS-051 @VS-070 @VS-036
  Scénario: Préparée au portail sans placer de zone, l'enveloppe est signée à la main ; la signature figure sur le document scellé
    Quand j'exécute le cas "portail.preparateur"
    Alors la plateforme se comporte comme attendu
    Quand je me connecte au portail en tant que "prep-sig-${unique}@exemple.ca"
    Et je clique sur "Enveloppes"
    Et je clique sur "Nouvelle enveloppe"
    Et je saisis "PORTAIL-${UNIQUE}" dans le champ "Référence"
    Et je choisis "Document" dans la liste "Mode de l'enveloppe"
    Et je saisis "${dansUnMois}" dans le champ "Date limite de signature"
    Et je joins un document PDF dans le champ "Document à signer"
    Et je saisis "Léa Fortin" dans le champ "Nom complet"
    Et je saisis "lea-${unique}@exemple.ca" dans le champ "Courriel"
    Et je choisis "Invité" dans la liste "Type de signataire"
    Alors l'écran est accessible, sans débordement ni superposition
    Quand je clique sur "Envoyer pour signature"
    Alors je vois "PORTAIL-${UNIQUE}"
    Quand j'exécute le cas "portail.enveloppe-envoyee"
    Alors la plateforme se comporte comme attendu
    Quand j'ouvre l'écran "Signataire — signature manuscrite"
    Et je coche "J'ai lu et j'accepte"
    Et je clique sur "Continuer"
    Alors je vois "Les zones de signature et d'initiales du document seront remplies par votre signature."
    Quand je clique sur "Dessiner"
    Et je dessine ma signature dans le "Cadre de signature"
    Et je clique sur "Signer le document"
    Alors je vois "Merci, votre signature est enregistrée."
    Quand j'exécute le cas "portail.enveloppe-verification"
    Alors la plateforme se comporte comme attendu

  @VS-035 @VS-060 @VS-061 @VS-051 @VS-070 @VS-036
  Scénario: Un Word déposé au portail est affiché ; la préparatrice y place initiales et signature où elle veut, puis la signataire signe
    Quand j'exécute le cas "portail.preparateur"
    Alors la plateforme se comporte comme attendu
    Quand je me connecte au portail en tant que "prep-sig-${unique}@exemple.ca"
    Et je clique sur "Enveloppes"
    Et je clique sur "Nouvelle enveloppe"
    Et je saisis "WORD-${UNIQUE}" dans le champ "Référence"
    Et je saisis "${dansUnMois}" dans le champ "Date limite de signature"
    Et je joins le document Word "contrat.docx" dans le champ "Document à signer"
    Et je saisis "Léa Fortin" dans le champ "Nom complet"
    Et je saisis "lea-${unique}@exemple.ca" dans le champ "Courriel"
    Et je clique sur "Placer les signatures sur le document"
    Alors je vois "Document à signer"
    Et je vois "Page 1 / 1"
    Quand je choisis "Initiales" dans la liste "Type de zone"
    Et je clique sur "Ajouter une zone à la page 1"
    Et je clique sur "Déplacer vers le haut"
    Et je choisis "Signature" dans la liste "Type de zone"
    Et je clique sur "Ajouter une zone à la page 1"
    Et je clique sur "Déplacer vers le bas"
    Alors je vois "2 zone(s) placée(s)"
    Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
    Quand je clique sur "Annuler"
    Alors je vois "Initiales — Léa Fortin"
    Quand je clique sur "Rétablir"
    Et je clique sur "Envoyer pour signature"
    Alors je vois "enveloppe(s) affichée(s)"
    Et je vois "WORD-${UNIQUE}"
    Quand j'exécute le cas "portail.enveloppe-placee"
    Alors la plateforme se comporte comme attendu
    Quand j'ouvre l'écran "Signataire — signature manuscrite"
    Et je coche "J'ai lu et j'accepte"
    Et je clique sur "Continuer"
    Et je clique sur "Dessiner"
    Et je dessine ma signature dans le "Cadre de signature"
    Et je clique sur "Signer le document"
    Alors je vois "Merci, votre signature est enregistrée."
    Quand j'exécute le cas "portail.enveloppe-verification"
    Alors la plateforme se comporte comme attendu
