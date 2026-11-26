package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.RefusAcces;
import java.util.Optional;

/**
 * Contrôle appliqué à chaque requête authentifiée, après la vérification du jeton : un jeton valide ne suffit pas si ce
 * qu'il engage a été suspendu ou révoqué depuis son émission (VS-010, VS-013). Chaque contexte apporte ses contrôles.
 */
public interface ControleAcces {

    Optional<RefusAcces> verifier(ContexteAcces contexte);
}
