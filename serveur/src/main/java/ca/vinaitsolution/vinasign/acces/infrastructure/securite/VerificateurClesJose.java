package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.port.VerificateurClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliquesInvalides;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.text.ParseException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Règles d'une JWKS d'application (VS-011, VS-013) : une ou deux clés (deux pendant une rotation), publiques seulement,
 * de signature, RSA d'au moins 2048 bits ou EC P-256, d'algorithme RS256 ou ES256 s'il est précisé, sans doublon.
 */
@Component
class VerificateurClesJose implements VerificateurClesPubliques {

    static final int MAX_CLES = 2;
    private static final int RSA_MIN_BITS = 2048;

    @Override
    public ClesPubliques verifier(String jwks) {
        JWKSet jeu;
        try {
            jeu = JWKSet.parse(jwks == null ? "" : jwks);
        } catch (ParseException e) {
            throw new ClesPubliquesInvalides("JWKS illisible.");
        }
        if (jeu.getKeys().isEmpty() || jeu.getKeys().size() > MAX_CLES) {
            throw new ClesPubliquesInvalides("Une ou deux clés attendues (deux pendant une rotation).");
        }
        Set<String> empreintes = new LinkedHashSet<>();
        for (JWK cle : jeu.getKeys()) {
            verifierCle(cle);
            if (!empreintes.add(empreinte(cle))) {
                throw new ClesPubliquesInvalides("La même clé est déclarée deux fois.");
            }
        }
        return new ClesPubliques(jeu.toString(true), empreintes);
    }

    private static void verifierCle(JWK cle) {
        if (cle.isPrivate()) {
            throw new ClesPubliquesInvalides("Une clé privée a été transmise : ne déclarer que des clés publiques, et renouveler celle-ci.");
        }
        if (cle.getKeyUse() != null && !KeyUse.SIGNATURE.equals(cle.getKeyUse())) {
            throw new ClesPubliquesInvalides("Seules des clés de signature sont acceptées.");
        }
        boolean rsa = cle instanceof RSAKey r && r.size() >= RSA_MIN_BITS
                && (cle.getAlgorithm() == null || JWSAlgorithm.RS256.equals(cle.getAlgorithm()));
        boolean ec = cle instanceof ECKey e && Curve.P_256.equals(e.getCurve())
                && (cle.getAlgorithm() == null || JWSAlgorithm.ES256.equals(cle.getAlgorithm()));
        if (!rsa && !ec) {
            throw new ClesPubliquesInvalides("Clé refusée : RSA d'au moins 2048 bits (RS256) ou EC P-256 (ES256).");
        }
    }

    static String empreinte(JWK cle) {
        try {
            return cle.computeThumbprint().toString();
        } catch (JOSEException e) {
            throw new ClesPubliquesInvalides("Empreinte de clé incalculable.");
        }
    }
}
