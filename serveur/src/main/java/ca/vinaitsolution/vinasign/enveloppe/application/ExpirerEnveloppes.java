package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.enveloppe.application.port.EcheancesEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** Expiration à échéance (VS-025), par la plateforme : chaque enveloppe envoyée et échue passe à « expirée ». */
public class ExpirerEnveloppes {

    static final String ACTEUR = "plateforme:expiration";

    private final EcheancesEnveloppes echeances;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;
    private final int lot;

    public ExpirerEnveloppes(EcheancesEnveloppes echeances, GenerateurIdentifiant identifiants, Clock horloge, int lot) {
        this.echeances = echeances;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.lot = lot;
    }

    /** Rend le nombre d'enveloppes expirées. */
    public int executer() {
        Instant maintenant = horloge.instant();
        int n = 0;
        for (Enveloppe e : echeances.echues(maintenant, lot)) {
            Enveloppe expiree = e.expirer(maintenant);
            echeances.expirer(expiree, new EvenementEnveloppe(identifiants.suivant(), e.id(), e.organisation(), "enveloppe-expiree", ACTEUR,
                    maintenant, Map.of()));
            n++;
        }
        return n;
    }
}
