package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Signataire authentifié par sa session (VS-040, VS-046) : il n'agit que sur son enveloppe, au nom de l'organisation de
 * celle-ci. Son nom d'acteur est opaque.
 */
public class AuthentificationSignataire extends AbstractAuthenticationToken {

    public static final String ROLE = "ROLE_signataire";

    private final IdentifiantOrganisation organisation;
    private final UUID enveloppe;
    private final UUID signataire;
    private final UUID session;

    /** @param session session ouverte ; nulle pendant l'ouverture elle-même */
    public AuthentificationSignataire(IdentifiantOrganisation organisation, UUID enveloppe, UUID signataire, UUID session) {
        super(List.of(new SimpleGrantedAuthority(ROLE)));
        this.organisation = organisation;
        this.enveloppe = enveloppe;
        this.signataire = signataire;
        this.session = session;
        setAuthenticated(true);
    }

    public UUID session() {
        return session;
    }

    public IdentifiantOrganisation organisation() {
        return organisation;
    }

    public UUID enveloppe() {
        return enveloppe;
    }

    public UUID signataire() {
        return signataire;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return "signataire:" + signataire;
    }
}
