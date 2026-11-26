package ca.vinaitsolution.vinasign.commun.infrastructure.evenements;

import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.application.port.PublicationEvenements;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Publication synchrone, dans la transaction de l'appelant : un abonné en échec annule l'action qui a publié (un envoi
 * ne réussit pas sans ses liens d'invitation). Les abonnés sont résolus à la première publication (cycles de création).
 */
@Component
class PublicationSynchrone implements PublicationEvenements {

    private final List<AbonneEvenements> abonnes;

    PublicationSynchrone(@Lazy List<AbonneEvenements> abonnes) {
        this.abonnes = abonnes;
    }

    @Override
    public void publier(EvenementMetier evenement) {
        for (AbonneEvenements a : abonnes) {
            if (a.concerne(evenement.type())) {
                a.recevoir(evenement);
            }
        }
    }
}
