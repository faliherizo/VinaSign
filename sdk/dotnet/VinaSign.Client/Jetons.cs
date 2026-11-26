using System.Net.Http.Headers;
using System.Text.Json;

namespace VinaSign.Client;

/// <summary>Identité de l'application auprès de VinaSign ; l'émetteur vaut par défaut l'adresse de l'API.</summary>
public sealed record Identite(Uri Serveur, string ClientId, System.Security.Cryptography.AsymmetricAlgorithm Cle, IReadOnlyList<string> Portees,
    string? Emetteur = null, string? Kid = null)
{
    public string EmetteurEffectif => Emetteur ?? Serveur.ToString().TrimEnd('/');
}

/// <summary>
/// Jeton d'accès (client credentials, private_key_jwt), mis en cache jusqu'à 30 secondes de son échéance ; posé sur chaque
/// requête par un gestionnaire HTTP.
/// </summary>
public sealed class Jetons : DelegatingHandler
{
    private readonly Identite identite;
    private readonly HttpClient http;
    private readonly TimeProvider horloge;
    private readonly SemaphoreSlim verrou = new(1, 1);
    private string? jeton;
    private DateTimeOffset expire = DateTimeOffset.MinValue;

    public Jetons(Identite identite, HttpClient http, TimeProvider horloge, HttpMessageHandler interne) : base(interne)
    {
        this.identite = identite;
        this.http = http;
        this.horloge = horloge;
    }

    public async Task<string> ObtenirAsync(CancellationToken annulation = default)
    {
        await verrou.WaitAsync(annulation);
        try
        {
            if (jeton is not null && horloge.GetUtcNow() < expire.AddSeconds(-30))
            {
                return jeton;
            }
            long t = horloge.GetUtcNow().ToUnixTimeSeconds();
            string assertion = JwtSigne.Signer(identite.Cle, new Dictionary<string, object>
            {
                ["iss"] = identite.ClientId, ["sub"] = identite.ClientId, ["aud"] = identite.EmetteurEffectif, ["jti"] = Guid.NewGuid().ToString(),
                ["iat"] = t, ["exp"] = t + 60,
            }, identite.Kid);
            using var reponse = await http.PostAsync(new Uri(identite.Serveur, "/oauth2/token"), new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["grant_type"] = "client_credentials", ["client_id"] = identite.ClientId, ["scope"] = string.Join(' ', identite.Portees),
                ["client_assertion_type"] = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", ["client_assertion"] = assertion,
            }), annulation);
            string corps = await reponse.Content.ReadAsStringAsync(annulation);
            if (!reponse.IsSuccessStatusCode)
            {
                throw new JetonRefuseException($"Jeton refusé par VinaSign (HTTP {(int)reponse.StatusCode}) : {corps}");
            }
            using var json = JsonDocument.Parse(corps);
            jeton = json.RootElement.GetProperty("access_token").GetString();
            expire = horloge.GetUtcNow().AddSeconds(json.RootElement.TryGetProperty("expires_in", out var e) ? e.GetInt64() : 60);
            return jeton!;
        }
        finally
        {
            verrou.Release();
        }
    }

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage requete, CancellationToken annulation)
    {
        requete.Headers.Authorization = new AuthenticationHeaderValue("Bearer", await ObtenirAsync(annulation));
        return await base.SendAsync(requete, annulation);
    }
}

public sealed class JetonRefuseException(string message) : Exception(message);
