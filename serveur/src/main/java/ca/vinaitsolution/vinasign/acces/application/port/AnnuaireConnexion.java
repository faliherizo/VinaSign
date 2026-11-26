package ca.vinaitsolution.vinasign.acces.application.port;

import ca.vinaitsolution.vinasign.acces.domain.IdentiteExterne;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import java.util.Optional;

/**
 * Retrouve l'utilisateur d'une identité externe, quelle que soit son organisation (seule lecture permise avant que
 * l'organisation soit connue). À la première connexion, un utilisateur invité est lié à son identité si le fournisseur
 * atteste son courriel.
 */
public interface AnnuaireConnexion {

    Optional<UtilisateurPortail> authentifier(IdentiteExterne identite);
}
