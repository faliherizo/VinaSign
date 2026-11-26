using System.Security.Cryptography;
using System.Text;
using VinaSign.Client.Genere;

namespace VinaSign.Client;

/// <summary>Point d'entrée : client généré dont chaque requête porte le jeton de l'application. Ex. <c>Fabrique.Creer(identite).EnveloppeCreerAsync(...)</c>.</summary>
public static class Fabrique
{
    public static VinaSignApi Creer(Identite identite, HttpMessageHandler? transport = null, TimeProvider? horloge = null)
    {
        var interne = transport ?? new HttpClientHandler { AllowAutoRedirect = false };
        var jetons = new Jetons(identite, new HttpClient(interne, disposeHandler: false), horloge ?? TimeProvider.System, interne);
        return new VinaSignApi(new HttpClient(jetons) { BaseAddress = identite.Serveur });
    }
}

/// <summary>Attestation d'un utilisateur déjà authentifié par l'application (VS-044), à joindre à la demande de session intégrée.</summary>
public static class Attestations
{
    public static string Attester(Identite identite, string sujet, IReadOnlyList<string> methodes, DateTimeOffset authentifieLe, DateTimeOffset maintenant)
    {
        long t = maintenant.ToUnixTimeSeconds();
        return JwtSigne.Signer(identite.Cle, new Dictionary<string, object>
        {
            ["iss"] = identite.ClientId, ["sub"] = sujet, ["aud"] = identite.EmetteurEffectif + "/attestations", ["jti"] = Guid.NewGuid().ToString(),
            ["iat"] = t, ["exp"] = t + 60, ["amr"] = methodes, ["auth_time"] = authentifieLe.ToUnixTimeSeconds(),
        });
    }
}

/// <summary>
/// Vérification d'un webhook (VS-091) : « t=&lt;s&gt;,v1=&lt;HMAC-SHA256 de "t.corps"&gt; », en temps constant, refusé au-delà de la
/// tolérance. Ensuite : dédupliquer par VinaSign-Event-Id et relire l'enveloppe par l'API.
/// </summary>
public sealed class VinaSignWebhooks(string secret, TimeProvider horloge, TimeSpan tolerance)
{
    private readonly byte[] cle = Encoding.UTF8.GetBytes(secret);

    public bool Verifier(string? entete, byte[] corps)
    {
        var m = System.Text.RegularExpressions.Regex.Match(entete ?? "", "^t=(\\d{1,12}),v1=([0-9a-f]{64})$");
        if (!m.Success)
        {
            return false;
        }
        long t = long.Parse(m.Groups[1].Value);
        byte[] attendu = HMACSHA256.HashData(cle, Encoding.ASCII.GetBytes(t + ".").Concat(corps).ToArray());
        return Math.Abs(horloge.GetUtcNow().ToUnixTimeSeconds() - t) <= tolerance.TotalSeconds
            && CryptographicOperations.FixedTimeEquals(attendu, Convert.FromHexString(m.Groups[2].Value));
    }
}
