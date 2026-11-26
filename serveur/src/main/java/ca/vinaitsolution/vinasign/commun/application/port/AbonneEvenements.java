package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;

/** Contexte abonné aux événements métier d'un autre contexte. */
public interface AbonneEvenements {

    boolean concerne(String type);

    void recevoir(EvenementMetier evenement);
}
