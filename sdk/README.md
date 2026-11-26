# SDK VinaSign

Clients minces de l'API, générés à partir du contrat OpenAPI publié par le serveur (`/v3/api-docs`), complétés par ce
qu'un générateur ne fait pas. Le contrat de référence est versionné avec le serveur :
[`serveur/contrat/openapi-v1.json`](../serveur/contrat/openapi-v1.json) (VS-090) ; `ContratOpenApiTest` échoue s'il
diffère de l'API publiée. Les SDK n'embarquent **aucune règle de sécurité** ni aucune liste métier : les listes
viennent des référentiels de l'API ([VS-ADR-004](../docs/adr/VS-ADR-004-modes-d-integration.md)).

| Dossier | Contenu | Compatibilité | Story |
|---|---|---|---|
| `java/` | `vinasign-client`, `vinasign-spring-boot-starter` : client, vérification des webhooks, aide au calcul d'empreinte (canonicalisation, PDF externe avec PDFBox) | Java 17 et 21 (le contrat est en 17) | VS-092 |
| `ts/` | `@vinasign/client` (Node 20 et plus), `@vinasign/embed` (navigateur, sans dépendance : iframe et `postMessage` à origine stricte) | Node 20+, navigateurs à jour | VS-093 |
| `dotnet/` | `VinaSign.Client` : client généré (NSwag), jeton, attestations, webhooks | .NET 8 | VS-125 |

Chaque SDK exécute la suite de tests de contrat OpenAPI (VS-090) et se publie au registre privé avant la V1.
