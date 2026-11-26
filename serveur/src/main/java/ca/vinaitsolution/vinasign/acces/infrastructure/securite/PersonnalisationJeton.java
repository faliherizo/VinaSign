package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.commun.infrastructure.securite.OrganisationCouranteJeton;
import java.util.List;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/** Jeton d'accès : audience de l'API, organisation et environnement de l'application (que le client ne peut pas choisir), client. */
@Component
class PersonnalisationJeton implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /** Environnement de l'application (bac à sable ou production) : ses données ne se mêlent jamais à l'autre. */
    static final String REVENDICATION_ENVIRONNEMENT = "env";

    private final JetonsProprietes proprietes;

    PersonnalisationJeton(JetonsProprietes proprietes) {
        this.proprietes = proprietes;
    }

    @Override
    public void customize(JwtEncodingContext contexte) {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(contexte.getTokenType())) {
            contexte.getClaims()
                    .audience(List.of(proprietes.audience()))
                    .claim(OrganisationCouranteJeton.REVENDICATION,
                            contexte.getRegisteredClient().getClientSettings().<String>getSetting(ClientsEnregistres.ORGANISATION))
                    .claim("client_id", contexte.getRegisteredClient().getClientId())
                    .claim(REVENDICATION_ENVIRONNEMENT,
                            contexte.getRegisteredClient().getClientSettings().<String>getSetting(ClientsEnregistres.ENVIRONNEMENT));
        }
    }
}
