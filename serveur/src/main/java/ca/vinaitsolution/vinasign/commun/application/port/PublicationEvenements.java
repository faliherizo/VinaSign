package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;

/** Publication d'un événement métier aux contextes abonnés, dans la transaction de l'appelant. */
public interface PublicationEvenements {

    void publier(EvenementMetier evenement);
}
