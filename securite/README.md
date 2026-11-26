# Tests d'intrusion

Ces tests éprouvent **chaque opération** de l'API, lue dans l'inventaire OpenAPI publié par le serveur, sans liste
d'URL tenue à la main. Ils s'appuient sur OWASP ZAP (balayage dynamique) et sur Trivy (dépendances, secrets,
configuration). Le harnais est repris de `contractuelle_contrat/securite` (VS-111).

| Fichier | Rôle |
|---|---|
| `tests/NN-*.spec.ts` | Une famille OWASP par fichier ; le titre porte la catégorie : `INT-01 Surface anonyme [A01 · A07]` |
| `donnees/surface-publique.json` | Seule liste tenue à la main : les opérations publiques par conception, chacune motivée |
| `donnees/charges.json` | Charges d'attaque (injection SQL, XSS, traversée, SSRF, formules CSV, secrets interdits) |
| `donnees/constats.json`, `acceptations.json`, `conditions.json` | Registre des constats, alertes acceptées avec justification, conditions d'exploitation |
| `outils/rapport.mjs` | Rapport Markdown à partir des résultats réels |

```bash
npm ci
npx playwright test     # tests seuls, serveur en marche
./lancer.sh             # campagne complète (Docker requis pour ZAP et Trivy)
```

## Familles à porter au fil des stories

Le harnais du contrat compte dix familles. Huit sont portées : 01, 03, 05, 06, 07, 08, 09 et 10 (VS-111). Restent 02
(rôles du portail) et 04 (isolation entre signataires).

| Famille | Story déclencheuse |
|---|---|
| 02 Rôles et moindre privilège | VS-014 (rôles du portail) |
| 03 Isolation entre organisations (canaris) | VS-005 et VS-010 |
| 04 Isolation entre signataires | VS-040 et VS-046 |
| 05 Authentification, jetons, force brute | VS-012 et VS-042 |
| 08 Données sensibles, affectation de masse, cartes de paiement | VS-011 et VS-121 |
| 09 Fichiers téléversés | VS-021 |
| 10 SSRF | VS-091 (webhooks) |

## Ajouter une opération

Il n'y a rien à faire : l'inventaire est relu à chaque campagne. Une opération publique par conception s'ajoute,
motivée, à `surface-publique.json` ; sans cela, INT-01 échoue.
