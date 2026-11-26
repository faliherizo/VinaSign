package ca.vinaitsolution.vinasign.session.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Liens actifs d'enveloppes envoyées, toutes organisations confondues : seule lecture de la tâche de relance. */
public interface RelancesDues {

    /** Lien encore utilisable, avec son organisation, son émission et son rang de relance. */
    record LienActif(UUID organisation, UUID enveloppe, UUID signataire, UUID lien, Instant emisLe, int rang) {
    }

    List<LienActif> liensActifs(Instant maintenant, int limite);
}
