package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import ca.vinaitsolution.vinasign.acces.application.AuthentifierUtilisateur;
import ca.vinaitsolution.vinasign.acces.domain.Courriel;
import ca.vinaitsolution.vinasign.acces.domain.Habilitation;
import ca.vinaitsolution.vinasign.acces.domain.IdentiteExterne;
import ca.vinaitsolution.vinasign.commun.infrastructure.securite.AuthentificationUtilisateur;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Jeton du fournisseur d'identité → utilisateur du portail habilité. Le courriel n'est retenu que s'il est attesté
 * ({@code email_verified}) ; un utilisateur inconnu ou désactivé est refusé comme un jeton invalide (401).
 */
class ConversionUtilisateurPortail implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AuthentifierUtilisateur authentification;

    ConversionUtilisateurPortail(AuthentifierUtilisateur authentification) {
        this.authentification = authentification;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jeton) {
        Habilitation h = authentification.executer(identite(jeton))
                .orElseThrow(() -> new InvalidBearerTokenException("Utilisateur du portail inconnu ou désactivé."));
        List<SimpleGrantedAuthority> autorites = h.roles().stream().sorted().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        return new AuthentificationUtilisateur(jeton, h.utilisateur(), h.organisation(), autorites);
    }

    static IdentiteExterne identite(Jwt jeton) {
        Optional<Courriel> courriel = Boolean.TRUE.equals(jeton.getClaimAsBoolean("email_verified"))
                ? Optional.ofNullable(jeton.getClaimAsString("email")).flatMap(ConversionUtilisateurPortail::courriel) : Optional.empty();
        List<String> amr = jeton.getClaimAsStringList("amr");
        Set<String> methodes = amr == null ? Set.of() : new HashSet<>(amr);
        return new IdentiteExterne(jeton.getClaimAsString("iss"), jeton.getSubject(), courriel, methodes);
    }

    private static Optional<Courriel> courriel(String texte) {
        try {
            return Optional.of(new Courriel(texte));
        } catch (IllegalArgumentException malForme) {
            return Optional.empty();
        }
    }
}
