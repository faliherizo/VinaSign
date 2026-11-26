package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.session.application.port.RelancesDues;
import ca.vinaitsolution.vinasign.session.domain.Relances;
import java.time.Duration;
import java.time.Instant;

/**
 * Relances des signataires (VS-082) : quand la fréquence est écoulée, le lien du signataire est réémis (l'ancien est
 * révoqué, le jeton n'étant conservé nulle part en clair) et un courriel de relance part, jusqu'au plafond. Une
 * enveloppe annulée n'a plus de lien actif : elle n'est jamais relancée.
 */
public class RelancerSignataires {

    static final String ACTEUR = "plateforme:relances";

    private final RelancesDues dues;
    private final EmettreLiens liens;
    private final ExecutionOrganisation execution;
    private final ServicesSession services;
    private final Duration frequence;
    private final int maximum;

    public RelancerSignataires(RelancesDues dues, EmettreLiens liens, ExecutionOrganisation execution, ServicesSession services, Duration frequence,
                               int maximum) {
        this.dues = dues;
        this.liens = liens;
        this.execution = execution;
        this.services = services;
        this.frequence = frequence;
        this.maximum = maximum;
    }

    /** Rend le nombre de relances envoyées. */
    public int executer() {
        Instant maintenant = services.horloge().instant();
        int n = 0;
        for (RelancesDues.LienActif l : dues.liensActifs(maintenant, 500)) {
            if (Relances.relanceDue(l.emisLe(), l.rang(), maintenant, frequence, maximum)) {
                execution.pour(new IdentifiantOrganisation(l.organisation()), ACTEUR, () -> liens.relancer(l.enveloppe(), l.signataire(), l.lien(), l.rang() + 1));
                n++;
            }
        }
        return n;
    }
}
