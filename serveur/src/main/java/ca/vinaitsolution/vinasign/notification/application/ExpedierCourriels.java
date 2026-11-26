package ca.vinaitsolution.vinasign.notification.application;

import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.application.port.Messagerie;
import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import java.time.Clock;
import java.time.Instant;

/**
 * Expédition de la boîte d'envoi (VS-080) : chaque courriel dû est envoyé ; un échec est repris plus tard avec un
 * délai croissant, puis abandonné au-delà du plafond de tentatives. Rien n'est perdu entre l'action et l'envoi.
 */
public class ExpedierCourriels {

    private final BoiteEnvoi boite;
    private final Messagerie messagerie;
    private final Clock horloge;
    private final int lot;
    private final int tentativesMax;

    public ExpedierCourriels(BoiteEnvoi boite, Messagerie messagerie, Clock horloge, int lot, int tentativesMax) {
        this.boite = boite;
        this.messagerie = messagerie;
        this.horloge = horloge;
        this.lot = lot;
        this.tentativesMax = tentativesMax;
    }

    /** Rend le nombre de courriels envoyés. */
    public int executer() {
        int envoyes = 0;
        for (CourrielSortant c : boite.dus(horloge.instant(), lot)) {
            Instant maintenant = horloge.instant();
            try {
                messagerie.envoyer(c.destinataire(), c.sujet(), c.corps());
                boite.envoye(c.id(), maintenant);
                envoyes++;
            } catch (RuntimeException e) {
                int tentatives = c.tentatives() + 1;
                boite.echec(c.id(), tentatives, c.prochaineApresEchec(maintenant), tentatives >= tentativesMax);
            }
        }
        return envoyes;
    }
}
