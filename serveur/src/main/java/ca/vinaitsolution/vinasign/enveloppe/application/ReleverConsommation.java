package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalConsommation;
import ca.vinaitsolution.vinasign.enveloppe.domain.Consommation;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Relevé de consommation de l'organisation de l'appelant pour une période de jours, dans le fuseau de la plateforme (VS-120). */
public class ReleverConsommation {

    static final int JOURS_MAX = 400;

    private final ServicesEnveloppe services;
    private final JournalConsommation journal;
    private final ZoneId fuseau;

    public ReleverConsommation(ServicesEnveloppe services, JournalConsommation journal, ZoneId fuseau) {
        this.services = services;
        this.journal = journal;
        this.fuseau = fuseau;
    }

    public Consommation.Releve executer(LocalDate du, LocalDate au) {
        services.appelant();
        if (du == null || au == null || au.isBefore(du) || du.plusDays(JOURS_MAX).isBefore(au)) {
            throw new SaisieInvalide("periode-invalide", "Période invalide : « du » puis « au », " + JOURS_MAX + " jours au plus.");
        }
        Instant debut = du.atStartOfDay(fuseau).toInstant();
        Instant fin = au.plusDays(1).atStartOfDay(fuseau).toInstant();
        return Consommation.compter(journal.entre(debut, fin), journal.envoyeesAvant(debut), fuseau);
    }
}
