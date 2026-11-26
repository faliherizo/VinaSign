# VS-ADR-008 — Origines autorisées à intégrer l'interface du signataire : par session, depuis la base

- **Statut** : Proposé
- **Date** : 2026-11-22
- **Story** : VS-055 (complète VS-ADR-004)

## Contexte et problème

Une application cliente affiche la session intégrée d'un signataire (VS-046) dans une iframe de sa propre page. La
politique `frame-ancestors` de l'interface du signataire doit autoriser **cette** application et aucune autre.

Les origines autorisées sont déclarées par chaque application cliente et conservées en base (VS-013). La règle « tout est
dynamique » interdit de les recopier dans une configuration de déploiement. Une liste unique, union de toutes les
applications, permettrait en outre à l'application A d'intégrer une session de l'application B.

## Options envisagées

1. **Liste statique dans le frontal** (`VINASIGN_ANCETRES_AUTORISES`, union des origines). Écartée : redéploiement à
   chaque nouvelle application, et une application peut encadrer la session d'une autre.
2. **Page servie par le serveur Java** sous `/integre/{jeton}`, avec l'en-tête calculé. Écartée : le serveur d'API
   servirait des ressources de l'interface (fichiers Angular), ce qui mêle deux déploiements et deux origines.
3. **Frontal nginx qui interroge le serveur par `auth_request`** avant de servir `/integre/{jeton}`, et place les
   origines reçues dans `frame-ancestors`. **Retenue.**

## Décision

- Le serveur expose `GET /api/v1/signataire/integration/ancetres`. Le frontal lui transmet le jeton dans l'en-tête
  `X-Jeton-Integration`, jamais dans l'URL relayée. La réponse est toujours un `204`, sans cache, et porte l'en-tête
  `X-Ancetres` :
  - pour une session intégrée valide, les origines déclarées par l'application propriétaire de l'enveloppe, triées ;
  - dans tous les autres cas, `'none'` (lien d'invitation, jeton inconnu, application révoquée ou sans origine). La
    réponse ne révèle donc pas si le jeton existe.
- Le lien n'est **pas ouvert** par cette lecture : l'usage unique de la session intégrée est préservé.
- Toutes les autres pages (portail, lien d'invitation `/s/{jeton}`, accueil) restent en `frame-ancestors 'none'`.
- L'interface reçoit les mêmes origines avec la session (`originesIntegration`). Elle n'écrit ses avis `postMessage`
  qu'à l'origine exacte de la page parente (`location.ancestorOrigins`, à défaut le référent), et seulement si cette
  origine est déclarée.
- `@vinasign/embed` crée l'iframe, avec un titre accessible et `referrerpolicy="origin"`. Il n'écoute que les messages
  venus de l'origine de VinaSign **et** de l'iframe qu'il a créée.

## Conséquences

- **Positives** :
  - une nouvelle origine déclarée par une application s'applique dès la session suivante, sans redéploiement ;
  - une application ne peut encadrer que ses propres sessions.
- **Négatives** :
  - chaque affichage d'une page intégrée coûte un aller-retour au serveur (lecture indexée par empreinte de jeton) ;
  - le frontal dépend du serveur pour servir cette page : si le serveur ne répond pas, `auth_request` échoue en 500 et la
    page n'est pas servie. C'est un échec fermé, voulu.
- **Preuves** :
  - `SessionApiTest` (origines rendues, `'none'` dans chaque autre cas) ;
  - `web/nginx/essai/verifier.sh`, qui éprouve l'image nginx réelle ;
  - spécifications `AvisParent` et `@vinasign/embed`.
