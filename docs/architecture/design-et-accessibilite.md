# Système de design et accessibilité (WCAG 2.2 AA, SGQRI 008 3.0)

Le portail (préparatrices, administrateurs) et l'écran du signataire (grand public, lien reçu par courriel) partagent
un seul système de design, tiré de jetons CSS. Ce document décrit ces jetons, les composants qui en découlent, et la
conformité WCAG 2.2 niveau AA **critère par critère**, avec la façon dont chacun est vérifié.

## 1. Organisation

| Fichier | Rôle |
|---|---|
| `web/projects/commun/styles/jetons.css` | Jetons sur `:root` : couleurs (thème clair, thème sombre sous `prefers-color-scheme: dark`), typographie, espacements, rayons, ombres, focus, cible minimale, durée d'animation |
| `web/projects/commun/styles/base.css` | Éléments natifs (titres, liens, boutons, champs, tableaux, `details`) et composants récurrents (`.vs-carte`, `.bandeau`, `.vs-vide`, `.vs-chargement`, `.vs-actions`, `.vs-pastille`, `.evitement`, `.visuellement-masque`), mouvement réduit, couleurs forcées |
| `web/projects/{portail,signataire}/src/styles.css` | Importent les deux feuilles (`@import`) avant leurs propres règles, en construction comme en test |
| `web/projects/*/src/app/**/*.css` | Mise en page de chaque écran, **uniquement** par jetons |
| `qualite/contrastes.mjs`, `qualite/contrastes.test.mjs` | Calcul des rapports de contraste des paires de jetons, dans les deux thèmes ; refus de toute couleur écrite hors des jetons |

Règle : aucune feuille d'écran n'écrit de couleur (`#…`, `rgb()`, `hsl()`) ; un mélange passe par
`color-mix(…, var(--vs-…), transparent)`. Le test `aucune feuille de style des interfaces n'écrit de couleur hors des
jetons` échoue sinon, car une couleur en dur échapperait au calcul de contraste.

## 2. Jetons

### Couleurs

| Jeton | Clair | Sombre | Emploi |
|---|---|---|---|
| `--vs-fond` | `#f4f6f9` | `#0e1319` | Fond de page |
| `--vs-surface` | `#ffffff` | `#161d26` | Cartes, en-tête, champs |
| `--vs-surface-alt` | `#eaeff5` | `#1f2833` | En-têtes de tableau, groupes, champs en lecture seule |
| `--vs-texte` / `--vs-texte-doux` | `#172231` / `#4a5768` | `#e7edf4` / `#b3bfcc` | Texte courant / secondaire |
| `--vs-bordure` | `#6c7889` | `#8996a7` | Limite des champs et listes (≥ 3:1) |
| `--vs-separateur` | `#d5dce5` | `#2e3947` | Filets **décoratifs** seulement (jamais une limite de composant) |
| `--vs-primaire` / `-survol` / `--vs-sur-primaire` | `#0b57a4` / `#08437f` / `#fff` | `#8cc4ff` / `#b5d9ff` / `#0b1d2e` | Actions principales, lien actif, étape courante |
| `--vs-primaire-doux` | `#e6f0fa` | `#17304a` | Survol des boutons secondaires, appel à l'action |
| `--vs-lien` | `#0a4f96` | `#9ccdff` | Liens (toujours soulignés) |
| `--vs-focus` / `--vs-focus-inverse` | `#0b57a4` / `#fff` | `#ffc266` / `#ffc266` | Anneau de focus (3 px, décalé de 2 px) ; inverse sur le bandeau d'accueil |
| `--vs-erreur`, `--vs-succes`, `--vs-alerte` (+ `-fond`) | | | Messages et bandeaux d'état, toujours doublés d'un texte ou d'un symbole |
| `--vs-desactive-fond` / `-texte` | `#e3e8ee` / `#4f5b6b` | `#262f3a` / `#aab5c2` | Boutons désactivés (lisibles bien qu'exemptés par 1.4.3) |
| `--vs-heros-debut`, `-fin`, `--vs-sur-heros(-doux)` | | | Bandeau d'accueil de la vitrine |
| `--vs-document`, `--vs-zone-*` | `#ffffff`, … | (identiques) | Page d'un document et zones posées : blanche dans les deux thèmes, comme le papier |

L'écran du signataire applique la **couleur de l'organisation** (VS-123) aux actions principales en thème clair : le
serveur la refuse sous 4,5:1 avec le blanc (`Marque`). En thème sombre, la couleur du système la remplace, une couleur
de marque foncée n'étant pas garantie contre un fond sombre.

### Typographie, espacements, formes

- Police système (`system-ui`, …) : aucune police distante (CSP stricte, rendu immédiat). Tailles fluides par
  `clamp()` pour les titres (`--vs-taille-titre-1/2/3`, `--vs-taille-heros`), interligne 1,55, titres équilibrés.
- Espacements `--vs-espace-1` à `-7` (0,25 à 3 rem), gouttière fluide `--vs-gouttiere`, largeur de contenu 72 rem,
  largeur de lecture 44 rem.
- Rayons `--vs-rayon-s/m/l/rond`, ombres `--vs-ombre-1/2` (plus marquées en thème sombre).
- `--vs-cible: 44px` : hauteur et largeur minimales de tout bouton, lien d'action, champ et liste.

## 3. Composants

| Composant | Usage |
|---|---|
| `button` (secondaire), `.principal`, `.danger`, `.vs-bouton` (lien en forme de bouton) | Une seule action principale par zone ; « danger » pour le refus de signer |
| Champs, `fieldset`/`legend`, `[aria-invalid=true]` (bordure d'erreur 2 px **et** message texte relié par `aria-describedby`) | Formulaires |
| `.vs-carte` | Regroupement visuel (sections, formulaires, carte d'enveloppe) |
| `.bandeau` (`.erreur`, `.succes`) | Message d'état, d'erreur ou de fin ; `role="alert"` ou `role="status"` selon l'urgence |
| `.vs-chargement` | État de chargement (`role="status"`), indicateur animé figé sous `prefers-reduced-motion` |
| `.vs-vide` | État vide (liste sans enveloppe, document absent, aucune facture) avec l'action suivante |
| Progression du signataire | Liste ordonnée « Consentement → (Vérification) → Lecture et signature → Terminé », `aria-current="step"`, étapes faites annoncées en texte |
| Pied de page | Aide au même endroit sur chaque écran (3.2.6) |

## 4. Écrans

| Application | Écran | États contrôlés (BDD, axe-core et géométrie) |
|---|---|---|
| Portail | Vitrine `/` | 4 largeurs |
| Portail | Référentiels | téléphone, tablette, grand écran ; changement de langue |
| Portail | État du service | 4 largeurs |
| Portail | Inscription | 4 largeurs |
| Portail | Connexion | invitation à se connecter, rôle manquant : 4 largeurs chacun |
| Portail | Enveloppes (liste) | après connexion (vide ou non), avec l'enveloppe envoyée : 4 largeurs |
| Portail | Nouvelle enveloppe | formulaire vide (4 largeurs), formulaire rempli |
| Portail | Document de l'enveloppe | placement des zones en brouillon (4 largeurs), document envoyé en consultation (4 largeurs) |
| Portail | Facturation | administrateur connecté avec MFA : 4 largeurs |
| Signataire | Accueil sans lien | téléphone |
| Signataire | Consentement | 4 largeurs |
| Signataire | Vérification d'identité | choix du moyen, saisie du code : 4 largeurs chacun |
| Signataire | Document et signature | lecture et champs (4 largeurs), champs manquants, signature dessinée |
| Signataire | Refus | 4 largeurs |
| Signataire | Confirmation | 4 largeurs |
| Signataire | Erreur (lien déjà servi) | 4 largeurs |

L'étape commune `l'écran est accessible à 390, 768, 1280 et 1680 px de large` (`bdd/etapes/communes.ts`) vérifie, à
chaque largeur et dans l'état courant : aucune violation axe-core (`wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`,
`wcag22aa`), aucun débordement horizontal, aucune cible interactive sous 24 px, aucun élément interactif superposé à
un autre. Un nouvel écran ou un nouvel état est couvert en ajoutant cette étape au scénario qui l'atteint.

## 5. Conformité WCAG 2.2 AA, critère par critère

Légende des preuves : **A** = axe-core dans la BDD ; **G** = contrôle géométrique BDD (débordement, superposition,
cibles) ; **C** = `qualite/contrastes.test.mjs` ; **K** = test Karma ; **R** = revue de conception (non automatisable).

| Critère | Comment c'est satisfait | Preuve |
|---|---|---|
| 1.1.1 Contenu non textuel | Logos et pastilles décoratifs `aria-hidden` / `alt=""` ; cadre de signature `role="img"` nommé ; texte de chaque page PDF exposé hors écran | A, K |
| 1.2.2 / 1.2.3 / 1.2.5 Médias | Démonstration sous-titrée (`track` captions) et transcription textuelle des étapes | A, K, R |
| 1.3.1 Information et relations | Titres hiérarchiques, `fieldset`/`legend`, `label` liés, tableaux `th scope`, listes, `dl` | A |
| 1.3.2 Ordre séquentiel logique | Ordre du DOM = ordre visuel ; grille du signataire sans réordonnancement | R, G |
| 1.3.3 Caractéristiques sensorielles | Consignes sans référence à la couleur ni à la forme (« coin bleu » retiré de la consigne de placement) | R |
| 1.3.4 Orientation | Aucune orientation imposée | G (4 largeurs) |
| 1.3.5 Finalité des champs | `autocomplete` : `name`, `email`, `organization`, `one-time-code` | A, R |
| 1.4.1 Utilisation de la couleur | Lien actif : graisse + soulignement épais ; états : symbole (✓, !) et texte ; champ invalide : message texte | R |
| 1.4.3 Contraste (minimum) | Toutes les paires texte/fond des jetons ≥ 4,5:1, deux thèmes | C, A |
| 1.4.4 Redimensionnement du texte | Unités `rem`, `clamp()`, aucune hauteur fixe de texte | R |
| 1.4.5 Texte sous forme d'image | Aucun | R |
| 1.4.10 Redistribution | Mise en page fluide de 390 à 1680 px ; tableaux larges confinés à une région défilante focalisable | G |
| 1.4.11 Contraste du contenu non textuel | Limites des champs (`--vs-bordure`), boutons, focus, repères de zone ≥ 3:1 sur chaque surface | C |
| 1.4.12 Espacement du texte | Aucun conteneur de texte à hauteur fixe | R |
| 1.4.13 Contenu au survol | Aucun contenu apparaissant au survol | R |
| 2.1.1 / 2.1.2 Clavier, pas de piège | Tout est atteignable au clavier, y compris le placement de zones (flèches, Majuscule) et la signature (« Taper mon nom ») | K, R |
| 2.4.1 Contourner des blocs | Lien d'évitement « Aller au contenu » en tête de chaque page | A, K |
| 2.4.2 Titre de page | `<title>` et `h1` par écran | A |
| 2.4.3 Parcours du focus | Ordre du DOM | R |
| 2.4.4 Fonction du lien | Libellés explicites (« Voir la facture F-1 », « Retour aux enveloppes ») | A, R |
| 2.4.6 En-têtes et étiquettes | Titres de section et étiquettes descriptives | R |
| 2.4.7 Visibilité du focus | Anneau 3 px décalé, jeton `--vs-focus` ≥ 3:1 sur chaque surface ; inverse sur le bandeau d'accueil | C |
| 2.4.11 Focus non masqué (minimum) | Aucun en-tête ni pied fixés ; `scroll-padding` ; le lien d'évitement passe au premier plan quand il a le focus | G, R |
| 2.5.1 / 2.5.2 Gestes, annulation | Aucun geste multipoint ; tracé de signature annulable (« Effacer ») | R |
| 2.5.3 Étiquette dans le nom | Nom accessible = texte visible (les flèches de déplacement ont un nom explicite) | A |
| 2.5.7 Mouvements de glissement | Placement des zones : boutons Déplacer (←→↑↓), Plus/Moins large/haute, flèches du clavier ; poser une zone par « Ajouter une zone à la page n » ; signature au clavier par « Taper mon nom » | K (`placement-zones.spec`), BDD « Un Word déposé… » |
| 2.5.8 Taille de la cible (minimum) | Cibles ≥ 44 px (`--vs-cible`), au-delà du minimum de 24 px ; exception « équivalent » pour la poignée de redimensionnement de 16 px, doublée par les boutons de taille | G |
| 3.1.1 / 3.1.2 Langue | `lang` du document suit la langue choisie | K |
| 3.2.1 / 3.2.2 Focus, saisie | Aucun changement de contexte au focus ; le changement de langue ne déplace pas le focus | R |
| 3.2.3 / 3.2.4 Navigation et identification cohérentes | Même en-tête, même navigation, mêmes composants partout | R |
| 3.2.6 Aide cohérente | Pied de page identique sur chaque écran, même position : portail (administrateur, état du service), signataire (contacter l'expéditeur, lien personnel) | K (`app.spec`) |
| 3.3.1 / 3.3.3 Identification et suggestion d'erreur | Messages qui disent quoi corriger, reliés au champ (`aria-describedby`, `aria-invalid`), annoncés (`role="alert"`, `aria-live`) | K, BDD « Des champs obligatoires sont vides » |
| 3.3.2 Étiquettes ou instructions | Champs obligatoires signalés par texte d'aide et astérisque | A, R |
| 3.3.4 Prévention des erreurs | Refus de signer : étape de confirmation et retour possible ; signature : consentement explicite préalable | BDD (refus), K |
| 3.3.7 Saisie redondante | Aucune donnée redemandée dans un même parcours : le brouillon garde référence et échéance (lecture seule) après un dépôt refusé ; les valeurs des champs du signataire survivent au passage par l'écran de refus | K (`nouvelle-enveloppe.spec`), BDD |
| 3.3.8 Authentification accessible (minimum) | Aucun test cognitif : connexion par le fournisseur de l'organisation (OIDC) ; signataire par lien, clé d'accès (WebAuthn) ou code à usage unique (`autocomplete="one-time-code"`, collage permis) | R, K |
| 4.1.2 Nom, rôle, valeur | Éléments natifs ; modes de signature en `role="radio"` avec `aria-checked` ; zones `aria-pressed` ; une liste de référentiel n'apparaît qu'avec son libellé (code du référentiel si le chargement échoue) — défaut trouvé par la BDD sur la facturation pendant le chargement | A, K (`liste-referentiel.spec`) |
| 4.1.3 Messages d'état | `role="status"` / `aria-live` pour chargement, compte d'enveloppes, étapes d'envoi, résultat | A, K |
| 2.3.3 (AAA, appliqué) | `prefers-reduced-motion` : animations et transitions neutralisées, défilement doux seulement sans préférence | R |

### Écarts et limites connus

- **Document PDF lui-même** : son contenu visuel est rendu sur un canevas ; le texte de chaque page est exposé aux
  technologies d'assistance, mais un PDF source non balisé (tableaux, images sans texte) reste aussi accessible que
  le document fourni par l'organisation.
- **Couleur de l'organisation en thème sombre** : remplacée par la couleur du système (voir §2).
- Les critères marqués **R** ne sont pas vérifiables par un outil ; ils ont été revus sur chaque écran lors de la
  refonte (2026-11-26) et sont à revoir à chaque nouvel écran.

## 6. Vérifier

```bash
node --test qualite/contrastes.test.mjs                 # contrastes, deux thèmes, couleurs hors jetons
(cd web && for p in commun portail signataire; do npx ng test $p --watch=false; done)
(cd bdd && npx bddgen && npx playwright test)            # pile locale démarrée (ci.sh, étape 3)
```
