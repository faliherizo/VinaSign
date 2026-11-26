package ca.vinaitsolution.vinasign.notification.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Boîte d'envoi des SMS : le numéro y est chiffré, le texte effacé après l'envoi. */
public interface BoiteSms {

    /** Dépôt dans l'organisation courante. */
    void deposer(UUID id, UUID organisation, String numero, String texte, Instant prochaine);

    /** SMS dus, toutes organisations confondues, réservés contre une expédition concurrente. */
    List<SmsDu> dus(Instant maintenant, int limite);

    void envoye(UUID id, Instant le);

    void echec(UUID id, int tentatives, Instant prochaine, boolean abandonne);

    /** SMS à envoyer, numéro déchiffré. */
    record SmsDu(UUID id, String numero, String texte, int tentatives) {

        @Override
        public String toString() {
            return "SmsDu[" + id + ", tentatives=" + tentatives + "]";
        }
    }
}
