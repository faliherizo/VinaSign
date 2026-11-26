# Infrastructure

| Élément | Rôle | Story |
|---|---|---|
| `docker-compose.dev.yml` | Base PostgreSQL et Mailpit d'un poste ; identifiants lus dans `.env` | VS-003 |
| `bacs-a-sable/` | Services externes simulés comme les vrais : ICP et horodatage, pyHanko, veraPDF, Gotenberg. Repris du contrat, ports configurables | VS-030 |
| `terraform/` | Azure Canada Central et East : réseau privé, PostgreSQL Flexible, stockage immuable, Key Vault, Front Door | VS-003 |
| `helm/` | Déploiement du serveur et des interfaces (conteneurs non privilégiés, systèmes de fichiers en lecture seule) | VS-003 |
| `../web/nginx/default.conf.template` | Frontal des interfaces : en-têtes de sécurité, `frame-ancestors` tiré de l'environnement | VS-055 |

```bash
cp .env.exemple .env          # puis remplacer les valeurs
docker compose --env-file .env -f infra/docker-compose.dev.yml up -d
docker compose -f infra/bacs-a-sable/docker-compose.yml up -d
```
