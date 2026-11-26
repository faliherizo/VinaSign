package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clé de signature des jetons d'accès. En exploitation, elle vient du coffre de secrets (PEM) ; une clé générée au
 * démarrage n'est permise que si la configuration l'autorise explicitement (poste, test). Sans l'une ni l'autre, le
 * serveur refuse de démarrer. La clé passera dans le HSM avec le port CleDeSignature (VS-031).
 */
@Configuration
public class ClesDeJeton {

    @Bean
    JWKSource<SecurityContext> sourceClesJeton(JetonsProprietes proprietes) {
        return new ImmutableJWKSet<>(new JWKSet(cle(proprietes)));
    }

    static RSAKey cle(JetonsProprietes proprietes) {
        try {
            if (proprietes.clePriveePem() != null && !proprietes.clePriveePem().isBlank()) {
                return depuisPem(proprietes.clePriveePem());
            }
            if (!proprietes.cleEphemereAutorisee()) {
                throw new IllegalStateException("Clé de signature des jetons absente : fournir VINASIGN_JETONS_CLE_PRIVEE "
                        + "(coffre de secrets), ou autoriser une clé éphémère sur un poste de développement.");
            }
            KeyPairGenerator generateur = KeyPairGenerator.getInstance("RSA");
            generateur.initialize(3072);
            var paire = generateur.generateKeyPair();
            return identifiee(new RSAKey.Builder((RSAPublicKey) paire.getPublic()).privateKey(paire.getPrivate()));
        } catch (GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException("Clé de signature des jetons inutilisable.", e);
        }
    }

    static RSAKey depuisPem(String pem) throws GeneralSecurityException, JOSEException {
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        KeyFactory fabrique = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privee = (RSAPrivateCrtKey) fabrique.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        RSAPublicKey publique = (RSAPublicKey) fabrique.generatePublic(new RSAPublicKeySpec(privee.getModulus(), privee.getPublicExponent()));
        return identifiee(new RSAKey.Builder(publique).privateKey(privee));
    }

    /** Identifiant de clé = empreinte RFC 7638 : stable pour une même clé, sans valeur choisie à la main. */
    private static RSAKey identifiee(RSAKey.Builder constructeur) throws JOSEException {
        RSAKey provisoire = constructeur.keyUse(KeyUse.SIGNATURE).build();
        return new RSAKey.Builder(provisoire).keyID(provisoire.computeThumbprint().toString()).build();
    }
}
