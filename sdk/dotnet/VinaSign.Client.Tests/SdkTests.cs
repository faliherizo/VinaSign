using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.Extensions.Time.Testing;
using VinaSign.Client;
using Xunit;

namespace VinaSign.Client.Tests;

/// <summary>Faux transport : rend le jeton au point /oauth2/token, une liste de référentiels ailleurs ; garde les requêtes vues.</summary>
sealed class FauxVinaSign : HttpMessageHandler
{
    public readonly List<(string Chemin, string? Autorisation, string Corps)> Vues = new();
    public HttpStatusCode StatutJeton = HttpStatusCode.OK;
    private int emis;

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage r, CancellationToken a)
    {
        string corps = r.Content is null ? "" : await r.Content.ReadAsStringAsync(a);
        Vues.Add((r.RequestUri!.AbsolutePath, r.Headers.Authorization?.ToString(), corps));
        string reponse = r.RequestUri.AbsolutePath == "/oauth2/token"
            ? $"{{\"access_token\":\"jeton-{++emis}\",\"expires_in\":300}}"
            : "[{\"code\":\"langue\",\"libelle\":\"Langue\",\"nombreValeurs\":2}]";
        var statut = r.RequestUri.AbsolutePath == "/oauth2/token" ? StatutJeton : HttpStatusCode.OK;
        return new HttpResponseMessage(statut) { Content = new StringContent(reponse, Encoding.UTF8, "application/json") };
    }
}

public class SdkTests
{
    private static Dictionary<string, JsonElement> Verifier(string jwt, AsymmetricAlgorithm publique)
    {
        string[] p = jwt.Split('.');
        byte[] D(string s) => Convert.FromBase64String(s.Replace('-', '+').Replace('_', '/').PadRight(s.Length + (4 - s.Length % 4) % 4, '='));
        byte[] donnees = Encoding.ASCII.GetBytes(p[0] + "." + p[1]);
        bool ok = publique is RSA rsa ? rsa.VerifyData(donnees, D(p[2]), HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1)
            : ((ECDsa)publique).VerifyData(donnees, D(p[2]), HashAlgorithmName.SHA256, DSASignatureFormat.IeeeP1363FixedFieldConcatenation);
        Assert.True(ok, "signature du JWT");
        return JsonSerializer.Deserialize<Dictionary<string, JsonElement>>(D(p[1]))!;
    }

    [Fact]
    public async Task Le_jeton_est_obtenu_par_assertion_signee_mis_en_cache_renouvele_et_ses_refus_signales()
    {
        using var rsa = RSA.Create(2048);
        var faux = new FauxVinaSign();
        var horloge = new FakeTimeProvider(DateTimeOffset.Parse("2026-11-22T12:00:00Z"));
        var identite = new Identite(new Uri("https://api.vinasign.test"), "app-1", rsa, new[] { "enveloppes-lire", "enveloppes-creer" }, "https://auth.vinasign.test", "k1");
        var jetons = new Jetons(identite, new HttpClient(faux), horloge, faux);
        Assert.Equal("jeton-1", await jetons.ObtenirAsync());
        Assert.Equal("jeton-1", await jetons.ObtenirAsync());
        var formulaire = System.Web.HttpUtility.ParseQueryString(faux.Vues[0].Corps);
        Assert.Equal("client_credentials", formulaire["grant_type"]);
        Assert.Equal("enveloppes-lire enveloppes-creer", formulaire["scope"]);
        var assertion = Verifier(formulaire["client_assertion"]!, rsa);
        Assert.Equal("app-1", assertion["iss"].GetString());
        Assert.Equal("https://auth.vinasign.test", assertion["aud"].GetString());
        horloge.Advance(TimeSpan.FromSeconds(271));
        Assert.Equal("jeton-2", await jetons.ObtenirAsync());
        faux.StatutJeton = HttpStatusCode.Unauthorized;
        horloge.Advance(TimeSpan.FromHours(1));
        await Assert.ThrowsAsync<JetonRefuseException>(() => jetons.ObtenirAsync());
    }

    [Fact]
    public async Task Le_client_genere_porte_le_jeton_et_l_emetteur_par_defaut_est_l_adresse_de_l_api()
    {
        using var rsa = RSA.Create(2048);
        var faux = new FauxVinaSign();
        var api = Fabrique.Creer(new Identite(new Uri("https://api.vinasign.test/"), "app-1", rsa, Array.Empty<string>()), faux);
        var referentiels = await api.ReferentielListerAsync(null);
        Assert.Equal("langue", referentiels.First().Code);
        Assert.Equal("https://api.vinasign.test", Verifier(System.Web.HttpUtility.ParseQueryString(faux.Vues[0].Corps)["client_assertion"]!, rsa)["aud"].GetString());
        Assert.Equal(("/api/v1/referentiels", "Bearer jeton-1"), (faux.Vues[1].Chemin, faux.Vues[1].Autorisation));
    }

    [Fact]
    public void Es256_au_format_jose_et_cles_inadaptees_refusees()
    {
        using var ec = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        Assert.Equal("ES256", JwtSigne.Algorithme(ec));
        Assert.Equal(7, Verifier(JwtSigne.Signer(ec, new Dictionary<string, object> { ["n"] = 7 }, "k"), ec)["n"].GetInt32());
        using var p384 = ECDsa.Create(ECCurve.NamedCurves.nistP384);
        Assert.Throws<ArgumentException>(() => JwtSigne.Algorithme(p384));
        using var dsa = DSA.Create();
        Assert.Throws<ArgumentException>(() => JwtSigne.Algorithme(dsa));
    }

    [Fact]
    public void L_attestation_designe_l_utilisateur_et_sa_seconde_preuve()
    {
        using var rsa = RSA.Create(2048);
        var t = DateTimeOffset.Parse("2026-11-22T12:00:00Z");
        var r = Verifier(Attestations.Attester(new Identite(new Uri("https://api.vinasign.ca"), "app-1", rsa, Array.Empty<string>()), "medecin-42",
            new[] { "pwd", "otp" }, t.AddMinutes(-1), t), rsa);
        Assert.Equal("https://api.vinasign.ca/attestations", r["aud"].GetString());
        Assert.Equal("medecin-42", r["sub"].GetString());
        Assert.Equal("otp", r["amr"][1].GetString());
        Assert.Equal(t.AddMinutes(-1).ToUnixTimeSeconds(), r["auth_time"].GetInt64());
    }

    [Fact]
    public void Le_webhook_se_verifie_comme_le_serveur_le_signe()
    {
        // Vecteur partagé avec le serveur et les SDK Java et TypeScript.
        const string entete = "t=1000,v1=666c03ec35508aac04dd952534a53765722de9e065b99131f1ad2fa957180969";
        byte[] corps = Encoding.UTF8.GetBytes("{\"id\":\"1\"}");
        var w = new VinaSignWebhooks("secret-de-test", new FakeTimeProvider(DateTimeOffset.FromUnixTimeSeconds(1200)), TimeSpan.FromMinutes(5));
        Assert.True(w.Verifier(entete, corps));
        Assert.False(w.Verifier(entete, Encoding.UTF8.GetBytes("{}")));
        Assert.False(w.Verifier(entete.Replace("t=1000", "t=1001"), corps));
        Assert.False(w.Verifier(null, corps));
        Assert.False(new VinaSignWebhooks("secret-de-test", new FakeTimeProvider(DateTimeOffset.FromUnixTimeSeconds(2000)), TimeSpan.FromMinutes(5))
            .Verifier(entete, corps), "rejeu ancien");
    }

    [Fact]
    public void Chaque_operation_du_contrat_est_appelable_depuis_le_sdk()
    {
        using var contrat = JsonDocument.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "../../../../../../serveur/contrat/openapi-v1.json")));
        var operations = contrat.RootElement.GetProperty("paths").EnumerateObject()
            .SelectMany(p => p.Value.EnumerateObject().Select(o => o.Value.GetProperty("operationId").GetString()!)).ToList();
        var methodes = typeof(Genere.VinaSignApi).GetMethods().Select(m => m.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
        Assert.True(operations.Count > 40);
        Assert.Empty(operations.Where(o => !methodes.Contains(o + "Async")));
    }
}
