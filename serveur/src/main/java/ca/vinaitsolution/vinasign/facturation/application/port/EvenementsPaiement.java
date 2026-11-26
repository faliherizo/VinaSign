package ca.vinaitsolution.vinasign.facturation.application.port;

import java.time.Instant;

/** Événements du prestataire déjà traités (VS-121) : livrés au moins une fois, traités une seule. */
public interface EvenementsPaiement {

    /** Vrai la première fois que cet identifiant est vu ; faux ensuite. */
    boolean retenir(String id, Instant le);
}
