# VinaSign

Plateforme de signature électronique opérée par Vina IT Solution : enveloppes, signature tactile, signature
d'empreinte, cachet PAdES-B-LTA, horodatage RFC 3161, dossier de preuve vérifiable hors plateforme.

- Cadrage : [docs/architecture/00-LISEZMOI.md](docs/architecture/00-LISEZMOI.md)
- Décisions : [docs/adr/](docs/adr/)
- Backlog : [docs/architecture/05-Backlog-et-stories.md](docs/architecture/05-Backlog-et-stories.md)
- Règles de travail : [CLAUDE.md](CLAUDE.md)
- Chaîne complète : `./ci.sh`

## Démarrer

```bash
cp .env.exemple .env                                            # puis remplacer les valeurs
cd serveur && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run   # démarre aussi la base (Compose)
cd web && npm ci && npx ng serve portail                         # http://localhost:4300
```

API : http://localhost:8300/api/v1 · OpenAPI : http://localhost:8300/v3/api-docs
