package ca.vinaitsolution.vinasign.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.sql.Array;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Applications clientes d'essai, enregistrées comme le ferait l'administration (compte propriétaire), avec une paire
 * de clés générée : le test signe ses assertions comme le ferait le système appelant.
 */
@TestComponent
public class ApplicationsDeTest {

    /** Application enregistrée : identifiant, organisation, clé privée (détenue par l'appelant seulement). */
    public record Application(String clientId, UUID organisation, RSAKey cle) { }

    private final JdbcTemplate proprietaire;

    public ApplicationsDeTest(@LiquibaseDataSource DataSource proprietaire) {
        this.proprietaire = new JdbcTemplate(proprietaire);
    }

    public Application enregistrer(String... portees) {
        return enregistrer(UUID.randomUUID(), portees);
    }

    public Application enregistrer(UUID organisation, String... portees) {
        RSAKey cle = nouvelleCle();
        organisationActive(organisation);
        String clientId = "essai-" + UUID.randomUUID();
        proprietaire.execute((java.sql.Connection c) -> {
            Array tableau = c.createArrayOf("text", portees);
            try (var ps = c.prepareStatement("""
                    INSERT INTO application_cliente (tenant_id, client_id, nom, environnement, jwks, portees)
                    VALUES (?, ?, ?, 'bac-a-sable', CAST(? AS jsonb), ?)""")) {
                ps.setObject(1, organisation);
                ps.setString(2, clientId);
                ps.setString(3, "Application " + clientId);
                ps.setString(4, new JWKSet(cle.toPublicJWK()).toString());
                ps.setArray(5, tableau);
                ps.executeUpdate();
            }
            return null;
        });
        return new Application(clientId, organisation, cle);
    }

    /** L'organisation existe et est active (une application appartient toujours à une organisation existante). */
    public void organisationActive(UUID organisation) {
        proprietaire.update("""
                INSERT INTO organisation (id, nom, statut, plan, cree_le, modifie_le)
                SELECT ?, ?, 'active', v.code, now(), now() FROM referentiel_valeur v JOIN referentiel_type t ON t.id = v.type_id
                WHERE t.code = 'plan' AND v.attributs ->> 'parDefaut' = 'true' AND v.tenant_id IS NULL
                ON CONFLICT (id) DO NOTHING""", organisation, "Organisation " + organisation);
    }

    public void revoquer(Application a) {
        proprietaire.update("UPDATE application_cliente SET statut = 'revoquee', revoque_le = now() WHERE client_id = ?", a.clientId());
    }

    /** Assertion client conforme (RFC 7523) ; chaque champ peut être altéré par le test. */
    public static String assertion(RSAKey cle, String iss, String sub, String aud, String jti, Instant emise, Instant expire) {
        try {
            JWTClaimsSet.Builder r = new JWTClaimsSet.Builder().issuer(iss).subject(sub).audience(aud)
                    .issueTime(Date.from(emise)).expirationTime(Date.from(expire));
            if (jti != null) r.jwtID(jti);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(cle.getKeyID()).build(), r.build());
            jwt.sign(new RSASSASigner(cle));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Jeton signé par une clé quelconque, pour éprouver le serveur de ressources. */
    public static String jeton(RSAKey cle, String iss, List<String> aud, UUID organisation, Instant expire) {
        try {
            JWTClaimsSet r = new JWTClaimsSet.Builder().issuer(iss).subject("forge").audience(aud).claim("org", organisation.toString())
                    .claim("scope", List.of("enveloppes-lire")).issueTime(Date.from(expire.minusSeconds(300)))
                    .expirationTime(Date.from(expire)).jwtID(UUID.randomUUID().toString()).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(cle.getKeyID()).build(), r);
            jwt.sign(new RSASSASigner(cle));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static RSAKey nouvelleCle() {
        try {
            return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
