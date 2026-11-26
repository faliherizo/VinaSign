package ca.vinaitsolution.vinasign.notification.infrastructure.sms;

import ca.vinaitsolution.vinasign.notification.application.port.Sms;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Simulateur de SMS (VS-081) : le message est consigné dans la table {@code sms_simule}, que le bac à sable consulte
 * comme le ferait le destinataire. Refusé en production par la configuration.
 */
public class SmsSimulateur implements Sms {

    private final JdbcTemplate jdbc;

    public SmsSimulateur(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void envoyer(String numero, String texte) {
        jdbc.update("INSERT INTO sms_simule (numero, texte) VALUES (?, ?)", numero, texte);
    }
}
