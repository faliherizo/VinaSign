package ca.vinaitsolution.vinasign.organisation.application;

import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.organisation.application.port.Marques;
import ca.vinaitsolution.vinasign.organisation.domain.Marque;
import ca.vinaitsolution.vinasign.organisation.domain.OrganisationIntrouvable;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** Marque de l'organisation de l'appelant (VS-123) : définie, puis lue ; la validation (dont le contraste) est celle du domaine. */
public class GererMarque {

    private final Marques marques;
    private final OrganisationCourante organisation;
    private final Clock horloge;

    public GererMarque(Marques marques, OrganisationCourante organisation, Clock horloge) {
        this.marques = marques;
        this.organisation = organisation;
        this.horloge = horloge;
    }

    public Marque definir(Marque marque) {
        marques.enregistrer(courante(), marque, horloge.instant());
        return marque;
    }

    public Optional<Marque> lire() {
        return marques.de(courante());
    }

    private UUID courante() {
        return organisation.courante().orElseThrow(OrganisationIntrouvable::new).valeur();
    }
}
