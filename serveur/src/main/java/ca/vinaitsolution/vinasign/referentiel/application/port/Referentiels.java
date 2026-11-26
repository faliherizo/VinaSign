package ca.vinaitsolution.vinasign.referentiel.application.port;

import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import java.util.List;
import java.util.Optional;

/** Lecture des référentiels. Contrat commun à toutes les implémentations (voir le test de contrat). */
public interface Referentiels {

    /** Tous les types, avec toutes leurs valeurs, actives ou non. */
    List<TypeReferentiel> types();

    Optional<TypeReferentiel> type(CodeReferentiel code);
}
