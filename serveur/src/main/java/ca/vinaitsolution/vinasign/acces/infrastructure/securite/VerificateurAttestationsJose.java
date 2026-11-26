package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.application.port.AssertionsConsommees;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.commun.application.port.VerificateurAttestations;
import ca.vinaitsolution.vinasign.commun.domain.Attestation;
import ca.vinaitsolution.vinasign.commun.domain.AttestationRefusee;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Attestation d'utilisateur (VS-044) : JWT signé RS256 ou ES256 par une clé enregistrée de l'application active, émis par
 * elle ({@code iss} = client_id), destiné aux attestations de VinaSign ({@code aud} = émetteur + {@value #AUDIENCE}, ce
 * qui interdit d'y réutiliser une assertion d'authentification du client), de courte durée et à usage unique ({@code jti}).
 */
@Component
class VerificateurAttestationsJose implements VerificateurAttestations {

    static final String AUDIENCE = "/attestations";
    private static final int TAILLE_MAX = 8_192;

    private final ApplicationsClientes applications;
    private final AssertionsConsommees assertions;
    private final JetonsProprietes proprietes;
    private final Clock horloge;

    VerificateurAttestationsJose(ApplicationsClientes applications, AssertionsConsommees assertions, JetonsProprietes proprietes, Clock horloge) {
        this.applications = applications;
        this.assertions = assertions;
        this.proprietes = proprietes;
        this.horloge = horloge;
    }

    @Override
    public Attestation verifier(String clientApplication, String assertion) {
        if (assertion == null || assertion.isBlank() || assertion.length() > TAILLE_MAX) {
            throw new AttestationRefusee("Attestation de l'utilisateur requise (JWT signé par l'application).");
        }
        IdentifiantClient client = new IdentifiantClient(clientApplication);
        ApplicationCliente application = applications.activeParClient(client)
                .orElseThrow(() -> new AttestationRefusee("Application inconnue ou révoquée."));
        JWTClaimsSet r = revendications(application, assertion);
        Instant maintenant = horloge.instant();
        exiger(clientApplication.equals(r.getIssuer()), "iss doit désigner l'application.");
        exiger(r.getAudience().contains(proprietes.emetteur() + AUDIENCE), "Audience de l'attestation incorrecte.");
        Instant expire = instant(r.getExpirationTime());
        Instant emise = r.getIssueTime() == null ? maintenant : instant(r.getIssueTime());
        exiger(expire != null && expire.isAfter(maintenant.minusSeconds(30)), "Attestation expirée.");
        exiger(Duration.between(emise, expire).compareTo(proprietes.dureeMaxAssertion()) <= 0 && !emise.isAfter(maintenant.plusSeconds(30)),
                "Durée de l'attestation hors limites.");
        exiger(r.getJWTID() != null && !r.getJWTID().isBlank() && r.getJWTID().length() <= 200, "Identifiant d'attestation (jti) requis.");
        exiger(assertions.consommer(r.getJWTID(), client, expire), "Attestation déjà utilisée.");
        return new Attestation(r.getSubject(), methodes(r), instant(dateOuNull(r)));
    }

    private static JWTClaimsSet revendications(ApplicationCliente application, String assertion) {
        try {
            DefaultJWTProcessor<SecurityContext> processeur = new DefaultJWTProcessor<>();
            processeur.setJWSKeySelector(new JWSVerificationKeySelector<>(DecodeurAssertionsClient.ALGORITHMES,
                    new ImmutableJWKSet<>(JWKSet.parse(application.clesPubliques()))));
            processeur.setJWTClaimsSetVerifier((revendications, contexte) -> { });
            return processeur.process(assertion, null);
        } catch (Exception e) {
            throw new AttestationRefusee("Attestation illisible ou signature invalide.");
        }
    }

    private static List<String> methodes(JWTClaimsSet r) {
        try {
            List<String> amr = r.getStringListClaim("amr");
            return amr == null ? List.of() : amr;
        } catch (java.text.ParseException e) {
            throw new AttestationRefusee("amr doit être une liste de méthodes.");
        }
    }

    private static Date dateOuNull(JWTClaimsSet r) {
        try {
            return r.getDateClaim("auth_time");
        } catch (java.text.ParseException e) {
            throw new AttestationRefusee("auth_time doit être une date (secondes).");
        }
    }

    private static Instant instant(Date d) {
        return d == null ? null : d.toInstant();
    }

    private static void exiger(boolean condition, String motif) {
        if (!condition) {
            throw new AttestationRefusee(motif);
        }
    }
}
