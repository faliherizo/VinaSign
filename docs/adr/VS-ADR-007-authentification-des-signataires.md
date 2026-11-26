# VS-ADR-007 — Authentification des signataires : invités, utilisateurs de l'application cliente, clés d'accès

- **Statut** : Proposé
- **Date** : 2026-11-22

## Contexte et problème

Trois populations signent :

- **des invités externes** (contrats IQ, Lévis), qui n'ont pas de compte ;
- **des utilisateurs déjà authentifiés dans l'application cliente** (professionnel du SAD qui signe sa note) ;
- **des signataires récurrents** (gestionnaire qui signe chaque semaine).

## Décision

- **Invité** :
  - lien porteur d'un jeton aléatoire de 256 bits, stocké haché ;
  - expiration, révocation et usage borné ;
  - code par courriel ou SMS selon le niveau, avec limitation des essais (portés du portail CLM).
- **Utilisateur de l'application cliente** :
  - le serveur de l'application demande une **session de signature** au nom de son utilisateur ;
  - VinaSign émet une session éphémère liée à l'enveloppe, au signataire, aux actions autorisées et à une durée de
    5 minutes, à usage unique ;
  - l'application atteste l'identité par une **assertion signée** par sa propre clé (JWT `private_key_jwt`, sujet,
    méthode d'authentification `amr`) ;
  - le niveau « renforcé » exige en plus une seconde preuve récente : `amr` contenant `otp`, `hwk` ou `mfa` datant de
    moins de 5 minutes, sinon une clé d'accès ou un code VinaSign.
- **Mise en œuvre de l'attestation (VS-044)** :
  - l'assertion porte `aud` = émetteur de VinaSign suivi de `/attestations`. Une assertion d'authentification du
    client (`aud` = émetteur) ne peut donc pas servir d'attestation, ni l'inverse ;
  - elle est signée par une clé enregistrée de l'application active ; `iss` vaut son `client_id`, et son `jti` est
    consommé dans le même registre anti-rejeu ;
  - le type de signataire qui l'exige porte l'attribut `exigeAttestation` du référentiel `type-signataire`. Aucun type
    n'est figé dans le code ;
  - le rang du niveau, les méthodes `amr` reconnues comme seconde preuve et leur âge maximal sont de la configuration
    (`vinasign.session.attestation.*`) ;
  - l'attestation retenue (sujet, `amr`, `auth_time`, niveau accordé) est conservée en ajout seul. La session ouverte
    part de ce niveau.
- **Clés d'accès** (WebAuthn) proposées aux signataires récurrents. C'est le moyen le plus résistant à
  l'hameçonnage.
- **Attestation** : le niveau d'authentification réellement atteint figure dans la preuve (champ `amr`, méthode,
  horodatage). On ne le déduit jamais a posteriori.

## Conséquences

- **SAD (US-408)** : ce modèle répond à RG-408-2, le TOTP à la signature. La question QM-16 se tranche en
  « renforcé » au pilote, et en « avancé » quand la V2 le permet.
- **Réutilisation de la confiance** : l'application cliente est responsable de l'identité qu'elle atteste. Le contrat
  d'application cliente le stipule, et VinaSign le consigne.
