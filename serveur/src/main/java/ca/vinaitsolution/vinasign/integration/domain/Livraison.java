package ca.vinaitsolution.vinasign.integration.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Livraison d'un événement à une destination (VS-091) : reprise à délai croissant (1 min, 5 min, 15 min, 1 h, puis toutes
 * les 4 h) tant que l'événement a moins de 72 h, puis abandon. Chaque tentative laisse sa trace (code HTTP ou motif).
 */
public record Livraison(UUID id, UUID destination, UUID evenement, Instant creeLe, int tentatives, Instant prochaineTentative,
                        Instant livreLe, Instant abandonneLe, Integer dernierCode) {

    public static final Duration FENETRE = Duration.ofHours(72);
    private static final List<Duration> DELAIS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));
    private static final Duration DELAI_ENSUITE = Duration.ofHours(4);

    public Livraison {
        if (id == null || destination == null || evenement == null || creeLe == null || prochaineTentative == null || tentatives < 0) {
            throw new IllegalArgumentException("Livraison incomplète.");
        }
    }

    public static Livraison nouvelle(UUID id, UUID destination, UUID evenement, Instant le) {
        return new Livraison(id, destination, evenement, le, 0, le, null, null, null);
    }

    public boolean terminee() {
        return livreLe != null || abandonneLe != null;
    }

    public Livraison reussie(int code, Instant le) {
        exigerEnCours();
        return new Livraison(id, destination, evenement, creeLe, tentatives + 1, prochaineTentative, le, null, code);
    }

    /** Échec (code HTTP, ou null si injoignable) : nouvelle tentative plus tard, ou abandon passé la fenêtre de 72 h. */
    public Livraison echouee(Integer code, Instant le) {
        exigerEnCours();
        int n = tentatives + 1;
        Instant suivante = le.plus(n <= DELAIS.size() ? DELAIS.get(n - 1) : DELAI_ENSUITE);
        if (!suivante.isBefore(creeLe.plus(FENETRE))) {
            return new Livraison(id, destination, evenement, creeLe, n, prochaineTentative, null, le, code);
        }
        return new Livraison(id, destination, evenement, creeLe, n, suivante, null, null, code);
    }

    private void exigerEnCours() {
        if (terminee()) {
            throw new IllegalStateException("Livraison déjà terminée.");
        }
    }
}
