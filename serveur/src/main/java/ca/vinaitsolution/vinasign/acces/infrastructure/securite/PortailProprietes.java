package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fournisseur d'identité OIDC du portail (VS-014), tiré de l'environnement. Sans émetteur, aucun utilisateur du portail
 * ne s'authentifie : seules les applications clientes accèdent à l'API.
 *
 * @param emetteur             {@code iss} des jetons du fournisseur (ex. Entra ID du locataire)
 * @param jwkSetUri            clés publiques du fournisseur
 * @param audience             audience exigée ({@code aud}) : l'identifiant de l'API VinaSign chez le fournisseur
 * @param methodesMultifacteur valeurs {@code amr} (RFC 8176) reconnues comme authentification multifacteur
 */
@ConfigurationProperties("vinasign.portail")
public record PortailProprietes(String emetteur, String jwkSetUri, String audience, List<String> methodesMultifacteur) {

    public PortailProprietes {
        methodesMultifacteur = methodesMultifacteur == null ? List.of() : List.copyOf(methodesMultifacteur);
    }

    public boolean estConfigure() {
        return emetteur != null && !emetteur.isBlank();
    }
}
