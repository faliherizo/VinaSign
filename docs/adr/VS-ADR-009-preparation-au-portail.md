# VS-ADR-009 — Préparation et envoi d'enveloppes depuis le portail, sans application cliente

- **Statut** : Proposé
- **Date** : 2026-11-22
- **Stories** : VS-060, VS-061, VS-062, VS-064 (et l'écran « Facturation » de VS-121)
- **Décideur** : responsable de VinaSign. L'option recommandée a été validée en séance le 2026-11-22, sous réserve de ce
  document.

## Contexte et problème

Aujourd'hui, **seule une application cliente** crée une enveloppe :

- `ServicesEnveloppe.appelant()` exige une organisation **et** un identifiant de client OAuth (`ApplicationRequise`
  sinon) ;
- l'enveloppe porte ce client dans `application`, et une application ne voit que ses propres enveloppes ;
- les routes exigent des portées (`SCOPE_enveloppes-creer`, etc.) que seul le serveur d'autorisation accorde à un client.

Le portail authentifie ses utilisateurs auprès du fournisseur d'identité de l'organisation (OIDC, VS-014). Ils portent
des **rôles** (`preparateur`, `administrateur-organisation`, `auditeur`), pas des portées. Une organisation sans
application (petite clinique, pilote interne) doit pourtant pouvoir préparer et envoyer une enveloppe depuis le portail,
sur téléphone, tablette et ordinateur (VS-064).

## Facteurs de décision

- Un seul moteur d'enveloppes : aucune règle métier dupliquée pour le portail.
- Isolation inchangée : une application ne voit pas les enveloppes d'une autre, ni celles du portail.
- Preuve : le journal nomme la **personne** qui a préparé et envoyé, pas un compte technique.
- Refus par défaut : un rôle n'ouvre que les routes qui lui sont destinées.
- Pas de secret dans le navigateur ; jetons en mémoire seulement.

## Options envisagées

1. **Le portail agit comme une application de l'organisation, au nom de l'utilisateur connecté (retenue).**
   - Le jeton OIDC de l'utilisateur est accepté par l'API.
   - Pour un utilisateur du portail, l'« application » de l'appelant devient l'espace de noms réservé `portail`.
   - Le rôle `preparateur` ouvre les routes de préparation et d'envoi, en plus des portées des applications.
   - L'acteur consigné au journal reste l'utilisateur.
2. **Une application cachée par organisation**, enregistrée automatiquement, dont le portail obtient un jeton par
   échange de jeton (RFC 8693). Écartée :
   - un client et une clé de plus par organisation ;
   - un échange de jeton à opérer ;
   - le journal nommerait l'application, pas la personne, sauf à propager l'acteur par une revendication de plus.
3. **Un serveur intermédiaire pour le portail (BFF)** qui détient un client OAuth et appelle l'API. Écartée :
   - un déploiement de plus ;
   - une session côté serveur ;
   - une seconde surface d'attaque, pour un gain nul puisque l'API accepte déjà les jetons du fournisseur du portail.

## Décision

### Appelant « portail »

- `ContexteAccesJeton` rend, pour un jeton du fournisseur du portail portant le rôle `preparateur` ou `auditeur`, le
  client réservé `portail`.
- Aucune application ne peut porter ce code : les identifiants de clients sont générés, toujours préfixés `vs-`.
- Toutes les enveloppes préparées au portail d'une organisation partagent cet espace : les préparateurs d'une organisation
  voient les enveloppes du portail de leur organisation, jamais celles des applications.

### Autorisation des routes

Chaque route de préparation et d'envoi accepte **la portée de l'application ou le rôle** :

```
hasAuthority('SCOPE_enveloppes-creer') or hasRole('preparateur')
```

- L'auditeur lit, sans préparer : `hasAnyRole('preparateur', 'auditeur')` sur les seules routes de lecture (enveloppe,
  documents, preuve).
- L'administrateur de l'organisation n'est pas préparateur par défaut : les rôles se cumulent explicitement.
- INT-02 (VS-111) éprouve chaque rôle contre tout l'inventaire.

### Journal et preuve

- L'acteur est la référence de l'utilisateur du portail (`utilisateur:<id>`), comme aujourd'hui pour l'administration.
- Le certificat de réalisation nomme l'expéditeur.

### Webhooks

Une enveloppe du portail n'a pas d'application destinataire : aucun webhook n'est émis. Le suivi se fait au portail et
par courriel.

### Connexion du portail

- OIDC, code d'autorisation avec PKCE, dans le navigateur, avec une bibliothèque certifiée OpenID (`oidc-client-ts`).
- Jetons en mémoire seulement ; renouvellement silencieux ; déconnexion chez le fournisseur.
- L'émetteur, le client et les portées viennent de `config.json`, comme `apiUrl` ; sans ce bloc, le portail n'offre
  que ses pages publiques :

  ```json
  { "apiUrl": "", "languePrincipale": "fr", "referentielLangue": "langue",
    "connexion": { "emetteur": "https://connexion.exemple.ca/realms/vinasign", "client": "vinasign-portail", "portees": "openid profile email" } }
  ```

- Le frontal n'autorise, en plus de sa propre origine, que celle du fournisseur (`VINASIGN_FOURNISSEUR_IDENTITE`,
  valeur Helm `interfaces.portail.fournisseurIdentite`) pour les appels et le cadre de renouvellement silencieux.
- Après la connexion, seul un chemin du portail est rouvert, jamais une adresse externe. Le jeton n'est joint qu'aux
  appels de l'API VinaSign.

### Modèles (VS-062)

Un modèle est un objet de l'organisation :

- rôles de signataires ;
- champs placés en coordonnées normalisées ;
- version.

Une enveloppe créée depuis un modèle en recopie la version. Modifier un modèle ne change jamais une enveloppe déjà créée.

## Conséquences

- **Positives** :
  - un seul moteur ;
  - la petite organisation signe sans intégration ;
  - le journal nomme la personne ;
  - l'écran « Facturation » (VS-121) devient possible, le portail étant authentifié.
- **Négatives** :
  - l'API accepte deux familles de jetons sur les mêmes routes ; les tests d'autorisation (INT-02) doivent couvrir
    chaque rôle ;
  - le code `portail` devient un identifiant réservé (hors de l'espace `vs-` des clients générés).
- **À faire** :
  - `ContexteAccesJeton` et les expressions d'autorisation (faits avec ce document) ;
  - la lecture par l'auditeur (faite le 2026-11-23) ;
  - la connexion OIDC du portail (faite le 2026-11-23, avec l'écran « Facturation ») ;
  - l'éditeur de champs tactile (VS-060), l'historique annuler et rétablir (VS-061), les modèles (VS-062) ;
  - le parcours d'envoi aux quatre largeurs (VS-064) : liste et envoi faits le 2026-11-23 ; contrôle axe des écrans
    connectés à brancher sur l'OIDC d'essai du harnais BDD.
