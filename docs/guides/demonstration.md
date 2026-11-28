# Démonstration de VinaSign : comptes, tutoriel et vidéo

> Pour qui présente ou essaie VinaSign sur <https://vinasign.vinaitsolution.com>. Tout ce qui est montré est réel — vrai
> contrat PDF, vrais scellement et horodatage — mais l'autorité de certification est **de démonstration** (non reconnue
> par Adobe Reader) et les courriels sont **capturés**, pas délivrés.

## 1. Adresses

| Adresse | À quoi elle sert |
|---|---|
| <https://vinasign.vinaitsolution.com> | vitrine (vidéo de démonstration) et portail : préparer, placer les zones, envoyer, suivre |
| <https://signer.vinasign.vinaitsolution.com> | écran du signataire (on y arrive par le lien du courriel) |
| <https://courriel.vinasign.vinaitsolution.com> | boîte des courriels de la démonstration : invitations des signataires, codes, confirmations |
| <https://id.vinasign.vinaitsolution.com> | page de connexion (Keycloak) ; on y est envoyé par « Se connecter » |

## 2. Comptes

Les mots de passe ne sont **jamais** dans le dépôt : ils sont tirés au hasard sur le serveur et rangés dans
`~/projets/VinaSign/deploy/ovh/secrets/comptes-demo.txt` (lisible du seul compte `deploy`) :

```bash
ssh deploy@51.222.86.37 cat ~/projets/VinaSign/deploy/ovh/secrets/comptes-demo.txt
```

| Compte | Rôle | Ce qu'il peut faire |
|---|---|---|
| `plateforme@vinasign.vinaitsolution.com` | administrateur de la plateforme | organisations, plans, applications clientes — **par l'API seulement** : le portail n'a pas encore d'écran pour ce rôle, la connexion laisse donc sur la vitrine |
| `admin@riviere-claire.vinasign.vinaitsolution.com` | administratrice de l'organisation de démonstration (Municipalité de Rivière-Claire) | utilisateurs et rôles de l'organisation, facturation |
| `preparation@riviere-claire.vinasign.vinaitsolution.com` | préparatrice et auditrice (Rivière-Claire) | préparer, envoyer, suivre les enveloppes, télécharger documents et preuves |
| `demo` (boîte des courriels) | — | lire les invitations envoyées aux signataires |

**Première connexion** : le mot de passe est provisoire. Keycloak demande d'en choisir un (12 caractères au moins), puis
d'**enrôler un code à usage unique** : scanner le code QR avec une application d'authentification (Microsoft
Authenticator, Google Authenticator, 1Password…), puis saisir le code affiché. Chaque connexion suivante demande ce code.
Les signataires, eux, n'ont **pas de compte** : ils signent par le lien personnel reçu par courriel.

## 3. Tutoriel : faire signer un contrat

1. **Se connecter** : sur la vitrine, « Se connecter au portail », avec le compte de la **préparatrice**
   (`preparation@riviere-claire…`). Après Keycloak, le portail ouvre directement « Enveloppes » ; l'administratrice arrive
   sur « Facturation ». La vitrine reste accessible par « Accueil ».
2. **Nouvelle enveloppe** : menu « Enveloppes » › « Nouvelle enveloppe ».
   - Référence : votre numéro de dossier (unique dans l'organisation) ; date limite de signature.
   - Document : un **PDF** ou un **Word** (.docx, converti en PDF/A au dépôt), 25 Mo au plus ; l'antivirus l'analyse.
   - Signataires : nom et courriel de chacun. « Faire signer dans l'ordre » : chacun n'est invité qu'à son tour ; sinon, tous
     en parallèle.
   - « **Placer les signatures sur le document** ».
3. **Placer les zones** : le document s'affiche page par page.
   - Choisir le signataire et le type de zone (signature, initiales, texte, date, case à cocher).
   - Toucher l'endroit voulu : la zone s'y pose. La glisser pour la déplacer, tirer son coin bleu pour la redimensionner.
     Au clavier : flèches pour déplacer, Majuscule + flèches pour la taille, Suppr pour la retirer. « Annuler » et
     « Rétablir » reprennent les 50 derniers gestes.
   - Un signataire sans zone de signature en reçoit une, d'office, en bas de la dernière page.
   - « Enregistrer le brouillon » pour y revenir plus tard, ou « **Envoyer pour signature** ».
4. **Signer** (côté signataire) : ouvrir la boîte <https://courriel.vinasign.vinaitsolution.com>, cliquer le lien de
   l'invitation.
   - Consentir à la signature électronique, lire le document : les zones du signataire y sont repérées.
   - Remplir les champs demandés (date, case…), puis « **Dessiner** » sa signature au doigt, au stylet ou à la souris — ou
     « Taper mon nom » — et « Signer le document ». Les initiales sont apposées par le même geste.
5. **Le document final** : quand tous ont signé, la plateforme appose les signatures, **scelle** le document (PAdES-B-LTA)
   et le fait **horodater** (RFC 3161). Au portail, « Enveloppes » › la référence : le document final s'affiche, avec
   « Télécharger le PDF ». Le certificat de réalisation et le dossier de preuve sont disponibles par l'API.
6. **Vérifier** : n'importe qui, sans compte, peut soumettre le PDF final à `POST /api/v1/verifications`
   (corps `application/pdf`) : la réponse dit s'il est intègre, qui l'a scellé, quand, et à quel niveau.

## 4. La vidéo de la vitrine

La vidéo est **tournée par un test de bout en bout** : [`bdd/demo/demonstration.spec.ts`](../../bdd/demo/demonstration.spec.ts),
avec les données de [`bdd/donnees/demonstration.json`](../../bdd/donnees/demonstration.json) et un vrai contrat de deux
pages ([`bdd/donnees/fichiers/contrat-services.pdf`](../../bdd/donnees/fichiers/contrat-services.pdf), produit par
Gotenberg depuis `contrat-services.html`). Chaque geste est suivi d'une vérification — jusqu'au scellement PAdES-B-LTA et à
la vérification publique du document final : la vidéo ne montre que ce que le test a constaté. Narration en français
(voix macOS « Amélie »), sous-titres WebVTT, transcription en texte sur la vitrine.

Régénérer (plateforme démarrée comme pour la BDD, `ci.sh` étape 3) :

```bash
cd bdd
node outils/video.mjs voix       # clips de narration (cache medias/.voix)
node outils/video.mjs tourner    # le test, filmé
node outils/video.mjs monter     # medias/demo/vinasign-demo.{mp4,vtt,jpg}
node outils/video.mjs publier    # envoi vers ~/projets/VinaSign/deploy/ovh/medias/demo, monté dans le portail
```

Les médias ne sont pas versionnés ; sans eux, la vitrine affiche un message à la place du lecteur et garde la transcription.
