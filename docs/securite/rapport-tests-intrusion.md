# Rapport des tests d'intrusion — VinaSign

- **Version éprouvée** : dépôt sans commit
- **Date** : 2026-11-22
- **Verdict** : ✅ aucun constat bloquant

## 1. Surface publique (sans authentification)

| Opérations | Pourquoi publiques | Protection |
|---|---|---|
| `^GET /api/v1/referentiels(/\{code\})?$` | Libellés des listes métier, nécessaires à l'écran du signataire avant toute authentification. | Lecture seule, aucune donnée personnelle ni d'organisation ; codes validés ; limitation de débit à la passerelle. |

## 2. Tests d'intrusion automatisés

| Test | OWASP | Scénario | Résultat |
|---|---|---|---|
| INT-01 Surface anonyme | A01 · A07 | chaque opération non publique de l'inventaire refuse l'accès anonyme par un 401 | ✅ réussi |
| INT-01 Surface anonyme | A01 · A07 | les opérations publiques ne divulguent ni erreur interne, ni trace, ni secret | ✅ réussi |
| INT-06 Injection | A03 | aucune charge d'injection ne provoque d'erreur serveur ni ne divulgue la base | ✅ réussi |
| INT-07 Configuration de sécurité | A05 | l'API pose les en-têtes de sécurité sur ses réponses | ✅ réussi |
| INT-07 Configuration de sécurité | A05 | une origine étrangère n'obtient pas l'autorisation CORS | ✅ réussi |
| INT-07 Configuration de sécurité | A05 | les erreurs ne divulguent aucune trace et TRACE est fermé | ✅ réussi |
| INT-07 Configuration de sécurité | A05 | les frontaux posent CSP, HSTS et la protection contre l'encadrement | ✅ réussi |

Volume éprouvé :

- 0 opérations non publiques éprouvées sans authentification
- 2 opérations publiques inspectées
- 24 charges d'injection envoyées

## 3. Constats et corrections

Aucun constat.

## 4. Balayage dynamique OWASP ZAP

_Non exécuté._

## 5. Dépendances, secrets et configuration (Trivy, npm audit)

_Trivy non exécuté._

## 6. Conditions d'exploitation

- La passerelle de production (Front Door et WAF) applique la limitation de débit et le TLS ; ces tests visent le serveur.
- Les secrets de production viennent de Key Vault ; aucun n'est dans le dépôt (vérifié par Trivy).
