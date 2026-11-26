package ca.vinaitsolution.vinasign.session.infrastructure.webauthn;

import ca.vinaitsolution.vinasign.session.application.port.VerificationWebAuthn;
import ca.vinaitsolution.vinasign.session.domain.CleAccesRefusee;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import com.webauthn4j.server.ServerProperty;
import java.util.Base64;
import java.util.List;

/**
 * Cérémonies WebAuthn vérifiées par WebAuthn4J (VS-043) : origine, partie de confiance, défi, présence et vérification de
 * l'utilisateur exigées, ES256 ou RS256. L'attestation de l'authentificateur n'est pas exigée (gestionnaires de mots de passe,
 * clés synchronisées) : la clé s'enregistre après une autre preuve d'identité. Un compteur qui recule trahit un clone.
 */
public class VerificationWebAuthn4j implements VerificationWebAuthn {

    private static final List<PublicKeyCredentialParameters> ALGORITHMES = List.of(
            new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
            new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.RS256));

    private final WebAuthnManager webauthn = WebAuthnManager.createNonStrictWebAuthnManager();
    private final AttestedCredentialDataConverter convertisseur = new AttestedCredentialDataConverter(new ObjectConverter());

    @Override
    public Enregistree enregistrer(Ceremonie c, byte[] attestationObject, byte[] clientDataJson) {
        RegistrationData d = verifier(() -> webauthn.verify(new RegistrationRequest(attestationObject, clientDataJson),
                new RegistrationParameters(serveur(c), ALGORITHMES, true, true)), "Enregistrement");
        AttestedCredentialData cle = d.getAttestationObject().getAuthenticatorData().getAttestedCredentialData();
        return new Enregistree(Base64.getUrlEncoder().withoutPadding().encodeToString(cle.getCredentialId()), convertisseur.convert(cle),
                d.getAttestationObject().getAuthenticatorData().getSignCount());
    }

    @Override
    public long authentifier(Ceremonie c, byte[] donneesCle, long compteur, Assertion a) {
        CredentialRecordImpl cle = new CredentialRecordImpl(new NoneAttestationStatement(), null, null, null, compteur, convertisseur.convert(donneesCle),
                new AuthenticationExtensionsAuthenticatorOutputs<>(), null, null, null);
        AuthenticationData d = verifier(() -> webauthn.verify(new AuthenticationRequest(a.credentialId(), a.authenticatorData(), a.clientDataJson(),
                a.signature()), new AuthenticationParameters(serveur(c), cle, List.of(a.credentialId()), true, true)), "Authentification");
        return d.getAuthenticatorData().getSignCount();
    }

    /** Toute réponse illisible ou invalide de l'authentificateur devient un refus, motif à l'appui. */
    private static <T> T verifier(java.util.function.Supplier<T> v, String ceremonie) {
        try {
            return v.get();
        } catch (RuntimeException e) {
            throw new CleAccesRefusee(ceremonie + " par clé d'accès refusé : " + e.getMessage());
        }
    }

    private static ServerProperty serveur(Ceremonie c) {
        return new ServerProperty(new Origin(c.origine()), c.rpId(), new DefaultChallenge(c.defi()));
    }
}
