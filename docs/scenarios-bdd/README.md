# Scénarios BDD des exigences fonctionnelles — tous les appels d'offres

> Document généré par `bdd/outils/documenter.mjs` ; ne pas le modifier à la main.

Chaque exigence de chaque appel d'offres est rattachée à un ou plusieurs **scénarios Gherkin** (`bdd/features/`), exécutés de
bout en bout contre la plateforme réelle par Playwright. Les jeux de données et les **cas exécutables** (suites d'appels,
profils, statuts et valeurs attendus) sont en JSON (`bdd/donnees/`). Une exigence qui ne relève pas d'un test (service,
prix, référence, infrastructure) est déclarée **documentaire** avec sa justification ; un écart connu est **déclaré** et
garde son scénario en attente. La démarche est décrite dans [`bdd/README.md`](../../bdd/README.md).

| Appel d'offres | Document | Exigences | Par scénario | Réussies | Documentaires | Écarts | Sans objet |
|---|---|---|---|---|---|---|---|
| VinaSign — backlog produit | [vinasign.md](vinasign.md) | 78 | 19 | 0 | 11 | 0 | 0 |

Scénarios : 26 dans 12 fonctionnalités ; cas exécutables : 26.
Résultats : aucune exécution enregistrée.
