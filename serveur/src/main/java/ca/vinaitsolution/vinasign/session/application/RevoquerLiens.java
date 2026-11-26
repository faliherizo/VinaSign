package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.session.application.port.LiensSignature;
import java.time.Clock;
import java.util.UUID;

/** Une enveloppe annulée n'a plus de lien utilisable (VS-025) : tous ses liens sont révoqués dans la même transaction. */
public class RevoquerLiens implements AbonneEvenements {

    static final String ANNULEE = "enveloppe-annulee";

    private final LiensSignature liens;
    private final Clock horloge;

    public RevoquerLiens(LiensSignature liens, Clock horloge) {
        this.liens = liens;
        this.horloge = horloge;
    }

    @Override
    public boolean concerne(String type) {
        return ANNULEE.equals(type);
    }

    @Override
    public void recevoir(EvenementMetier evenement) {
        liens.revoquerDe(UUID.fromString(evenement.attributs().get("enveloppe")), horloge.instant());
    }
}
