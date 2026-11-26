package ca.vinaitsolution.vinasign.integration.application.port;

import java.util.Map;

/** Envoi HTTP d'un webhook ; rend le code HTTP reçu, ou lève {@code Injoignable}. Aucune redirection n'est suivie. */
public interface EnvoiWebhook {

    int envoyer(String url, Map<String, String> entetes, byte[] corps);

    /** Destination injoignable ou refusée par la garde des requêtes sortantes. */
    class Injoignable extends RuntimeException {

        public Injoignable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
