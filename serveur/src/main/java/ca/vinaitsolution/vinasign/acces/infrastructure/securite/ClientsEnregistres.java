package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.commun.application.port.StatutOrganisations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/**
 * Applications clientes vues par le serveur d'autorisation : client credentials seulement, authentifiées par
 * private_key_jwt, jetons autoportants de courte durée. L'enregistrement passe par le cas d'utilisation (VS-011),
 * jamais par ce dépôt.
 */
@Component
class ClientsEnregistres implements RegisteredClientRepository {

    static final String CLES_PUBLIQUES = "vinasign.cles-publiques";
    static final String ORGANISATION = "vinasign.organisation";
    static final String ENVIRONNEMENT = "vinasign.environnement";

    private final ApplicationsClientes applications;
    private final StatutOrganisations statuts;
    private final JetonsProprietes proprietes;

    ClientsEnregistres(ApplicationsClientes applications, StatutOrganisations statuts, JetonsProprietes proprietes) {
        this.applications = applications;
        this.statuts = statuts;
        this.proprietes = proprietes;
    }

    @Override
    public void save(RegisteredClient client) {
        throw new UnsupportedOperationException("Les applications clientes s'enregistrent par le portail (VS-011).");
    }

    /** L'identifiant interne est le client_id : un seul chemin de lecture, le même contrôle. */
    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        IdentifiantClient client;
        try {
            client = new IdentifiantClient(clientId);
        } catch (IllegalArgumentException malForme) {
            return null;
        }
        // L'application d'une organisation suspendue est inconnue du serveur d'autorisation : aucun jeton (VS-010).
        return applications.activeParClient(client).filter(a -> !statuts.estBloquee(a.organisation())).map(this::enregistrement)
                .orElse(null);
    }

    private RegisteredClient enregistrement(ApplicationCliente a) {
        return RegisteredClient.withId(a.client().valeur())
                .clientId(a.client().valeur())
                .clientName(a.nom())
                .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(s -> s.addAll(a.portees()))
                .clientSettings(ClientSettings.builder()
                        .setting(CLES_PUBLIQUES, a.clesPubliques())
                        .setting(ORGANISATION, a.organisation().valeur().toString())
                        .setting(ENVIRONNEMENT, a.environnement())
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(proprietes.duree())
                        .build())
                .build();
    }
}
