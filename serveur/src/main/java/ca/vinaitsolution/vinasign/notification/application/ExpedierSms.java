package ca.vinaitsolution.vinasign.notification.application;

import ca.vinaitsolution.vinasign.notification.application.port.BoiteSms;
import ca.vinaitsolution.vinasign.notification.application.port.Sms;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Expédition des SMS (VS-081), avec reprise à délai croissant (1, 2, 4… minutes, une heure au plus) et abandon au plafond. */
public class ExpedierSms {

    private final BoiteSms boite;
    private final Sms sms;
    private final Clock horloge;
    private final int lot;
    private final int tentativesMax;

    public ExpedierSms(BoiteSms boite, Sms sms, Clock horloge, int lot, int tentativesMax) {
        this.boite = boite;
        this.sms = sms;
        this.horloge = horloge;
        this.lot = lot;
        this.tentativesMax = tentativesMax;
    }

    public int executer() {
        int envoyes = 0;
        for (BoiteSms.SmsDu s : boite.dus(horloge.instant(), lot)) {
            Instant maintenant = horloge.instant();
            try {
                sms.envoyer(s.numero(), s.texte());
                boite.envoye(s.id(), maintenant);
                envoyes++;
            } catch (RuntimeException e) {
                int tentatives = s.tentatives() + 1;
                boite.echec(s.id(), tentatives, maintenant.plus(Duration.ofMinutes(Math.min(60, 1L << Math.min(s.tentatives(), 6)))),
                        tentatives >= tentativesMax);
            }
        }
        return envoyes;
    }
}
