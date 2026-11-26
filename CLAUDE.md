# VinaSign — instructions du projet

Plateforme de signature électronique opérée par Vina IT Solution : un service indépendant, appelé par nos
applications (contrat CLM, SAD et Télésoin de `soins-virtuel`, autres applications de `~/project`), puis vendu en SaaS.
Dossiers de référence : `docs/architecture/00-LISEZMOI.md`, ADR dans `docs/adr/` (préfixe `VS-ADR-`).

## Arborescence

| Dossier | Contenu |
|---|---|
| `serveur/` | Spring Boot 3.5, Java 21. Monolithe modulaire : `ca.vinaitsolution.vinasign.<contexte>.{domain,application,infrastructure}` |
| `web/` | Espace Angular : `projects/portail` (administration, préparation), `projects/signataire` (signature, origine propre, intégrable en iframe), `projects/commun` (configuration à l'exécution, référentiels, liste dynamique `<vs-liste-referentiel>`, traductions) ; `nginx/` frontal |
| `sdk/` | Clients minces (Java compatible 17, TypeScript, .NET) générés à partir d'OpenAPI ; aucune règle de sécurité |
| `securite/` | Tests d'intrusion sur tout l'inventaire OpenAPI, ZAP, Trivy |
| `perf/` | JMeter et gate SLI/SLO |
| `bdd/` | Scénarios Gherkin (français), cas JSON exécutables, documentation générée |
| `e2e/` | Playwright avec accès direct à la base : preuves de dynamisme (une valeur ajoutée en base apparaît dans l'interface) |
| `infra/` | Terraform, Helm ; `infra/bacs-a-sable/` : ICP, horodatage, pyHanko, veraPDF, Mailpit |

## Commandes

| Action | Commande |
|---|---|
| Base locale | `cp .env.exemple .env` puis `docker compose --env-file .env -f infra/docker-compose.dev.yml up -d` |
| Bacs à sable | `docker compose -f infra/bacs-a-sable/docker-compose.yml up -d` |
| Tests serveur, avec portes de couverture | `cd serveur && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify` |
| Mutation | `cd serveur && mvn -Pmutation verify` |
| Serveur local | `cd serveur && mvn spring-boot:run` (port `VINASIGN_PORT`, défaut 8300) |
| Portail / signataire | `cd web && npx ng serve portail` (4300) · `npx ng serve signataire` (4301), relais `/api` vers le serveur |
| Tests des interfaces | `cd web && npx ng test commun --watch=false` (idem `portail`, `signataire`) ; `CHROME_BIN` requis |
| BDD / e2e / intrusion / perf | `cd bdd && npm test` · `cd e2e && npx playwright test` · `cd securite && ./lancer.sh` · `cd perf && ./run.sh` |
| Chaîne complète | `./ci.sh` (toutes les portes, dans l'ordre) |

Java 21 est obligatoire pour le serveur ; le JDK 25 par défaut du poste casse Mockito et JaCoCo.

## Règle « tout est dynamique »

Aucune donnée de référence n'est figée dans le code ni dans l'interface.

1. **Listes métier = référentiels en base.** Ce sont les langues, types de champ, niveaux d'authentification,
   canaux, motifs de refus, natures de document, etc.
   - Stockage : tables `referentiel_type` et `referentiel_valeur` (code, libellés par langue, attributs JSON, ordre,
     actif, organisation facultative).
   - Valeurs initiales : changesets Liquibase de données.
   - Exposition : `GET /api/v1/referentiels` et `GET /api/v1/referentiels/{type}`.
   - Interdits : un `enum` Java, une constante, un tableau TypeScript ou une `<option>` écrite à la main pour une liste
     métier.
2. **Les `enum` ne servent qu'aux invariants du domaine portés par du comportement** (machine d'états d'une
   enveloppe). Leur libellé affiché vient quand même d'un référentiel.
3. **Aucun identifiant en dur.**
   - Identifiants : UUID générés par un port `GenerateurIdentifiant`.
   - Données initiales : désignées par leur **code**.
   - Tests : ils découvrent ce qu'ils manipulent par l'API (inventaire OpenAPI, liste des référentiels), jamais
     `id = 1`.
4. **Interface : chaque liste déroulante, bouton radio ou case à cocher se charge depuis l'API.** Ses libellés viennent
   du référentiel dans la langue courante. L'ajout d'une valeur en base apparaît sans redéploiement.
5. **Configuration externe.** URL, ports, durées, seuils et fournisseurs passent par `@ConfigurationProperties` ou par
   l'environnement ; aucun hôte ni secret dans le code. Le choix d'un adaptateur est une configuration, jamais une
   condition dans le métier.
6. **Les tests le vérifient** : un test d'architecture refuse les `enum` hors des paquets `domain` autorisés, et un
   test e2e vérifie qu'une valeur ajoutée au référentiel apparaît dans la liste de l'interface.

## Normes héritées

- **Standards d'ingénierie** : ceux de `~/.claude/CLAUDE.md` s'appliquent sans exception.
  - clean architecture, SOLID ;
  - couverture ≥ 96 % par classe et agrégée, mutation ;
  - SGQRI 008 3.0 et WCAG 2.2 AA, aucune superposition, quatre largeurs ;
  - ADR au format MADR.
- **Sécurité** : refus par défaut. Toute route est authentifiée, sauf celles listées et motivées dans
  `securite/donnees/surface-publique.json` ; INT-01 le vérifie sur tout l'inventaire.
- **Données personnelles et secrets** : jamais journalisés, jamais dans le dépôt. Secrets par l'environnement ou Key
  Vault.
- **Commits** : jamais sans demande explicite.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **vinasign** (1129 symbols, 2100 relationships, 19 execution flows).

> Index stale? Run `node .gitnexus/run.cjs analyze --index-only` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? Bootstrap with `npx`, `bunx`, or `pnpm dlx` — e.g. `bunx gitnexus@latest analyze` (npm 11 npx crash; #1939).

## Always Do

- **MUST run impact before editing.** Use `impact({target: "symbolName", direction: "upstream"})` or `node .gitnexus/run.cjs impact "symbolName" --direction upstream --repo .`; report callers, processes, and risk. Never substitute grep for graph analysis.
- **MUST analyze graph changes before committing.** Use `detect_changes({scope: "all"})` (MCP) or `node .gitnexus/run.cjs detect-changes --scope all --repo .` (CLI fallback). `partial: true` or `truncated: true` is not a clean check — a zero means unseen, not unaffected; re-run it. For regression review: `detect_changes({scope: "compare", base_ref: "main"})` or `node .gitnexus/run.cjs detect-changes --scope compare --base-ref "main" --repo .`.
- MUST warn on HIGH/CRITICAL `risk` pre-edit; never use `riskSharedAxes` to waive a HIGH/CRITICAL `risk` warning. Compare File/symbol: MCP File omits axes; Graph-RAG expands File.
- **MUST treat `risk: UNKNOWN` as unresolved, not as low.** An empty caller set is not evidence the symbol is unused — it can also mean the callers are not resolvable by the index (plain-object property access, dynamic dispatch, cross-language calls). `impact` pairs `UNKNOWN` with a `riskNote` saying so. Confirm with a text search before treating the symbol as safe to change or delete; do not proceed on the strength of a zero.
- **MUST use `query({search_query: "concept"})` for concepts/flows, `context({name: "symbolName"})` for a named symbol, or `impact` for blast radius, on read-only callers, dependencies, imports, or execution flow.** Graph first; text search only for empty/`UNKNOWN`/literals.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method before MCP/CLI impact analysis.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis, and never read `UNKNOWN` as an all-clear — it means the walk could not answer, which is the one verdict that requires confirming by other means.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit before MCP/CLI graph change analysis.

## Resources

| Resource | Use for |
| --- | --- |
| `gitnexus://repo/vinasign/context` | Codebase overview, check index freshness |
| `gitnexus://repo/vinasign/clusters` | All functional areas |
| `gitnexus://repo/vinasign/processes` | All execution flows |
| `gitnexus://repo/vinasign/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
| --- | --- |
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
