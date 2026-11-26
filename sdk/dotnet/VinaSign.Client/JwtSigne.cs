using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace VinaSign.Client;

/// <summary>
/// JWT signé par la clé privée de l'application : RS256 (RSA) ou ES256 (EC P-256, signature R‖S de 64 octets).
/// La clé ne quitte jamais l'application ; VinaSign n'en connaît que la partie publique.
/// </summary>
public static class JwtSigne
{
    public static string Signer(AsymmetricAlgorithm cle, IReadOnlyDictionary<string, object> revendications, string? kid = null)
    {
        string alg = Algorithme(cle);
        var entete = new Dictionary<string, object> { ["alg"] = alg, ["typ"] = "JWT" };
        if (kid is not null)
        {
            entete["kid"] = kid;
        }
        string signe = B64(JsonSerializer.SerializeToUtf8Bytes(entete)) + "." + B64(JsonSerializer.SerializeToUtf8Bytes(revendications));
        byte[] donnees = Encoding.ASCII.GetBytes(signe);
        byte[] signature = cle is RSA rsa
            ? rsa.SignData(donnees, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1)
            : ((ECDsa)cle).SignData(donnees, HashAlgorithmName.SHA256, DSASignatureFormat.IeeeP1363FixedFieldConcatenation);
        return signe + "." + B64(signature);
    }

    public static string Algorithme(AsymmetricAlgorithm cle) => cle switch
    {
        RSA => "RS256",
        ECDsa ec when ec.KeySize == 256 => "ES256",
        _ => throw new ArgumentException("Clé refusée : RSA (RS256) ou EC P-256 (ES256)."),
    };

    internal static string B64(byte[] octets) => Convert.ToBase64String(octets).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}
