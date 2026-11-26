package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import java.util.Optional;
import java.util.UUID;

/** Lecture d'une enveloppe de l'organisation courante par un autre contexte (port de lecture, DTO). */
public interface LectureEnveloppes {

    Optional<DossierEnveloppe> dossier(UUID enveloppe);
}
