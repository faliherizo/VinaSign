package ca.vinaitsolution.vinasign.integration.application.port;

import ca.vinaitsolution.vinasign.integration.domain.Livraison;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Livraisons des webhooks : répartition de la boîte d'envoi, livraisons dues (toutes organisations), trace. */
public interface Livraisons {

    /** Livraison due, avec ce qu'il faut pour l'envoyer. */
    record Due(Livraison livraison, UUID organisation, String url, String secretChiffre, String type, UUID enveloppe, Instant survenuLe) {
    }

    /** Crée une livraison par destination concernée pour chaque événement sortant non encore réparti ; rend leur nombre. */
    int repartir(Instant le);

    List<Due> dues(Instant le, int limite);

    void remplacer(Livraison livraison);

    /** Trace des livraisons d'une destination, les plus récentes d'abord. */
    List<Livraison> de(UUID destination, int limite);
}
