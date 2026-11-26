package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.session.application.port.LiensSignature;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Après une signature ou un refus, les liens du signataire sont consommés : les rejouer rend 410 (VS-040). Un refus
 * termine l'enveloppe : les liens des autres signataires sont révoqués.
 */
public class ConsommerLiens implements AbonneEvenements {

    static final String SIGNE = "signataire-a-signe";
    static final String REFUSE = "signataire-a-refuse";

    private final LiensSignature liens;
    private final Clock horloge;

    public ConsommerLiens(LiensSignature liens, Clock horloge) {
        this.liens = liens;
        this.horloge = horloge;
    }

    @Override
    public boolean concerne(String type) {
        return SIGNE.equals(type) || REFUSE.equals(type);
    }

    @Override
    public void recevoir(EvenementMetier e) {
        Instant maintenant = horloge.instant();
        UUID signataire = UUID.fromString(e.attributs().get("signataire"));
        liens.actifs(signataire, maintenant).forEach(l -> liens.remplacer(l.consommer(maintenant)));
        if (REFUSE.equals(e.type())) {
            liens.revoquerDe(UUID.fromString(e.attributs().get("enveloppe")), maintenant);
        }
    }
}
