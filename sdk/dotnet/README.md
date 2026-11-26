# SDK .NET de VinaSign (VS-125)

- `VinaSign.Client` (.NET 8) : client généré à la construction depuis le contrat
  [`serveur/contrat/openapi-v1.json`](../../serveur/contrat/openapi-v1.json) par NSwag (`VinaSignApi`), plus le jeton
  `private_key_jwt` (`Jetons`, RS256 ou ES256), les attestations d'utilisateur (`Attestations`, VS-044) et la vérification
  des webhooks (`VinaSignWebhooks`, VS-091).
- `VinaSign.Client.Tests` : test de contrat (chaque opération du contrat a sa méthode), vecteur de signature des webhooks
  partagé avec le serveur et les autres SDK ; porte de couverture à 96 % sur le code écrit à la main.

```csharp
using var cle = RSA.Create();
cle.ImportFromPem(Environment.GetEnvironmentVariable("VINASIGN_CLE_PRIVEE")); // coffre de secrets, jamais le dépôt
var vinasign = Fabrique.Creer(new Identite(new Uri("https://api.vinasign.ca"), "mon-client", cle, new[] { "enveloppes-creer" }));
var referentiels = await vinasign.ReferentielListerAsync(null);
```

```bash
cd sdk/dotnet/VinaSign.Client.Tests && dotnet test
```
