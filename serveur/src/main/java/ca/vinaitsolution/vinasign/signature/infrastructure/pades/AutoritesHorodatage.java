package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.tsp.TimeStampToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autorités d'horodatage dans l'ordre de préférence (VS-033) : si l'une est indisponible ou rend une réponse
 * inadéquate (refus, jeton d'un autre document), la suivante prend le relais ; toutes en échec, l'horodatage échoue.
 */
final class AutoritesHorodatage {

    private static final Logger journal = LoggerFactory.getLogger(AutoritesHorodatage.class);

    private final List<AutoriteHorodatage> autorites;

    private AutoritesHorodatage(List<AutoriteHorodatage> autorites) {
        this.autorites = autorites;
    }

    /** Adresses séparées par des virgules ; vide : aucune autorité. */
    static AutoritesHorodatage depuis(String adresses, Clock horloge) {
        List<AutoriteHorodatage> liste = adresses == null ? List.of() : Arrays.stream(adresses.split(",")).map(String::strip)
                .filter(a -> !a.isEmpty()).map(a -> new AutoriteHorodatage(a, horloge)).toList();
        return new AutoritesHorodatage(liste);
    }

    boolean existe() {
        return !autorites.isEmpty();
    }

    TimeStampToken jeton(byte[] empreinteSha256) {
        IllegalStateException derniere = new IllegalStateException("Aucune autorité d'horodatage configurée.");
        for (int i = 0; i < autorites.size(); i++) {
            try {
                return autorites.get(i).jeton(empreinteSha256);
            } catch (IllegalStateException e) {
                journal.warn("Autorité d'horodatage {} sur {} en échec ; bascule vers la suivante.", i + 1, autorites.size());
                derniere = e;
            }
        }
        throw derniere;
    }
}
