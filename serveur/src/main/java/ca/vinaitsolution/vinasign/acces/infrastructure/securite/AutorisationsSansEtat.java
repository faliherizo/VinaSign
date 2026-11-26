package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

/**
 * Jetons autoportants de courte durée (client credentials) : rien n'est conservé côté serveur, ce qui évite une table
 * qui croît sans limite. La révocation passe par celle de l'application, vérifiée à chaque requête (VS-013).
 */
@Component
class AutorisationsSansEtat implements OAuth2AuthorizationService {

    @Override
    public void save(OAuth2Authorization autorisation) {
        // Rien à conserver : le jeton porte tout ce qu'il faut pour être vérifié.
    }

    @Override
    public void remove(OAuth2Authorization autorisation) {
        // Rien n'a été conservé.
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return null;
    }

    @Override
    public OAuth2Authorization findByToken(String jeton, OAuth2TokenType type) {
        return null;
    }
}
