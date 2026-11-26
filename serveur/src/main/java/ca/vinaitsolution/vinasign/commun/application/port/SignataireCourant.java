package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;
import java.util.Optional;

/** Signataire de la requête en cours, s'il est authentifié par sa session. */
public interface SignataireCourant {

    Optional<IdentiteSignataire> courant();
}
