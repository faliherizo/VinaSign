package ca.vinaitsolution.vinasign.enveloppe.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lecture seule : enveloppes d'une application (ou de l'espace portail) de l'organisation courante, les plus récentes d'abord. */
public interface ListeEnveloppes {

    /** Résumé d'une enveloppe pour une liste : aucun nom ni courriel de signataire. */
    record Resume(UUID id, String referenceExterne, String mode, String statut, Instant expireLe, Instant creeLe, int signataires, int signes) {
    }

    /** Position dans la liste : la dernière enveloppe déjà rendue. */
    record Position(Instant creeLe, UUID id) {
    }

    /** Au plus {@code limite} enveloppes de l'application, de ce statut s'il est donné, après la position donnée. */
    List<Resume> de(String application, Optional<String> statut, Optional<Position> apres, int limite);
}
