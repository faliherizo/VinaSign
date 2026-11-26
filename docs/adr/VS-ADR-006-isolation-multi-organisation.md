# VS-ADR-006 — Isolation multi-organisation en profondeur

- **Statut** : Proposé
- **Date** : 2026-11-22

## Contexte et problème

Une fuite entre organisations est le risque critique d'un SaaS de signature (dossier 1, § 17). Le contrat l'éprouve
par des canaris (INT-03) ; VinaSign doit faire au moins aussi bien, dès la conception.

## Décision

- **Application** :
  - chaque ressource porte `tenant_id` ;
  - l'organisation se déduit du jeton (application cliente) ou de la session, jamais d'un paramètre ;
  - les dépôts sont filtrés par un port `PorteeTenant`, ce qu'un test d'architecture vérifie.
- **Base de données** : sécurité au niveau des lignes de PostgreSQL (RLS), activée sur toutes les tables métier. Le
  rôle applicatif ne peut pas la contourner, et `app.tenant_id` est posé par transaction.
- **Stockage** : un conteneur ou un préfixe par organisation, et seulement des URL présignées de quelques minutes.
- **Clés** : une clé de chiffrement des données par organisation (chiffrement d'enveloppe) et un cachet par
  organisation. En V2, une option permet à l'organisation d'apporter sa propre clé.
- **Preuve** : l'isolation est éprouvée à chaque construction.
  - Les canaris de l'organisation A doivent rester invisibles et inaltérables depuis B, sur tout l'inventaire OpenAPI.
  - Ce sont les tests d'intrusion du contrat, portés.

## Mise en œuvre (VS-005, 2026-11-22)

- **Deux comptes** :
  - le **compte propriétaire** exécute les migrations ;
  - l'application se connecte **directement** sous un **compte applicatif** (`vinasign.base.role-applicatif`) :
    LOGIN, NOSUPERUSER, NOBYPASSRLS, propriétaire d'aucune table, sans aucun rôle endossable.
- **Pourquoi pas `SET ROLE`** : la première version endossait ce rôle depuis le compte propriétaire. Le test
  d'isolation a montré qu'une session peut alors revenir au propriétaire (`SET ROLE`, `RESET ROLE`) : une injection
  SQL contournerait la RLS. Le compte applicatif est donc provisionné hors des migrations, avec son mot de passe :
  - Terraform et Key Vault en production ;
  - `infra/base/initialisation` en développement ;
  - le support Testcontainers en test.

  Les migrations vérifient qu'il est inoffensif et refusent de s'exécuter sinon.
- **Organisation** : posée dans `app.organisation` à chaque emprunt d'une connexion, ou vidée. Les politiques s'y
  réfèrent.
- **Preuve** : `IsolationOrganisationTest` découvre dans le catalogue toute table portant `tenant_id` et exige sa RLS
  et une politique. Il vérifie aussi l'étanchéité A/B, l'absence de fuite d'organisation par le pool et le refus de
  toute escalade (SQLSTATE 42501).

## Conséquences

- **Contrainte d'implémentation** : la RLS impose de poser le contexte à chaque transaction. Un test vérifie qu'une
  requête sans contexte ne rend aucune ligne.
- **Instance dédiée** : pour un client entreprise, c'est le même code avec un tenant unique. Aucune bifurcation.
