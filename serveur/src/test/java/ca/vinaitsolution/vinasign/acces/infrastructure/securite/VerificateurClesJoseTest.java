package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliquesInvalides;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VerificateurClesJoseTest {

    private final VerificateurClesJose verificateur = new VerificateurClesJose();

    private static String jeu(JWK... cles) {
        return new JWKSet(List.of(cles)).toString(false);
    }

    private static RSAKey rsa(int bits) throws Exception {
        return new RSAKeyGenerator(bits).generate();
    }

    private static ECKey ec(Curve courbe) throws Exception {
        return new ECKeyGenerator(courbe).generate();
    }

    private static String motif(String jwks) {
        return assertThrows(ClesPubliquesInvalides.class, () -> new VerificateurClesJose().verifier(jwks)).getMessage();
    }

    @Test
    void une_cle_rsa_et_une_cle_ec_sont_acceptees_publiques_et_identifiees_par_leur_empreinte() throws Exception {
        RSAKey r = rsa(2048);
        ECKey e = ec(Curve.P_256);
        ClesPubliques cles = verificateur.verifier(jeu(r.toPublicJWK(), e.toPublicJWK()));
        assertEquals(Set.of(r.computeThumbprint().toString(), e.computeThumbprint().toString()), cles.empreintes());
        JWKSet relu = JWKSet.parse(cles.jwks());
        assertEquals(2, relu.getKeys().size());
        assertTrue(relu.getKeys().stream().noneMatch(JWK::isPrivate));
    }

    @Test
    void une_cle_de_signature_avec_son_algorithme_est_acceptee() throws Exception {
        RSAKey r = new RSAKey.Builder(rsa(3072).toRSAPublicKey()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        ECKey e = new ECKey.Builder(ec(Curve.P_256).toPublicJWK()).algorithm(JWSAlgorithm.ES256).build();
        assertEquals(2, verificateur.verifier(jeu(r, e)).empreintes().size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"pas du json", "{\"keys\":[]}", "{\"keys\":[{\"kty\":\"inconnu\"}]}"})
    void une_jwks_illisible_ou_vide_est_refusee(String jwks) {
        assertThrows(ClesPubliquesInvalides.class, () -> verificateur.verifier(jwks));
    }

    @Test
    void plus_de_deux_cles_sont_refusees() throws Exception {
        assertTrue(motif(jeu(rsa(2048).toPublicJWK(), rsa(2048).toPublicJWK(), rsa(2048).toPublicJWK())).contains("deux"));
    }

    @Test
    void une_cle_privee_est_refusee_et_le_message_dit_de_la_renouveler() throws Exception {
        assertTrue(motif(jeu(rsa(2048))).contains("renouveler"));
    }

    @Test
    void une_cle_de_chiffrement_est_refusee() throws Exception {
        assertTrue(motif(jeu(new RSAKey.Builder(rsa(2048).toRSAPublicKey()).keyUse(KeyUse.ENCRYPTION).build())).contains("signature"));
    }

    @Test
    void une_cle_faible_ou_d_une_autre_courbe_ou_d_un_autre_algorithme_est_refusee() throws Exception {
        var faible = java.security.KeyPairGenerator.getInstance("RSA");
        faible.initialize(1024);
        motif(jeu(new RSAKey.Builder((java.security.interfaces.RSAPublicKey) faible.generateKeyPair().getPublic()).build()));
        motif(jeu(ec(Curve.P_384).toPublicJWK()));
        motif(jeu(new RSAKey.Builder(rsa(2048).toRSAPublicKey()).algorithm(JWSAlgorithm.PS256).build()));
        motif(jeu(new ECKey.Builder(ec(Curve.P_256).toPublicJWK()).algorithm(JWSAlgorithm.RS256).build()));
    }

    @Test
    void la_meme_cle_declaree_deux_fois_est_refusee() throws Exception {
        RSAKey r = rsa(2048).toPublicJWK();
        RSAKey copie = new RSAKey.Builder(r).keyID("autre-identifiant").build();
        assertTrue(motif(jeu(r, copie)).contains("deux fois"));
    }

    @Test
    void une_empreinte_incalculable_rend_un_refus_et_non_une_erreur_interne() throws Exception {
        RSAKey cle = org.mockito.Mockito.mock(RSAKey.class);
        org.mockito.Mockito.when(cle.computeThumbprint()).thenThrow(new com.nimbusds.jose.JOSEException("SHA-256 indisponible"));
        assertThrows(ClesPubliquesInvalides.class, () -> VerificateurClesJose.empreinte(cle));
    }
}
