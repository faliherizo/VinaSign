package ca.vinaitsolution.vinasign.etat.application;

import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.etat.application.port.Incidents;
import ca.vinaitsolution.vinasign.etat.domain.Incident;
import ca.vinaitsolution.vinasign.etat.domain.IncidentIntrouvable;
import ca.vinaitsolution.vinasign.etat.domain.MiseAJourIncident;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publication des incidents par l'exploitation (VS-124) : impact, composants et phase sont des codes de référentiel
 * actifs ; la phase porte l'attribut {@code cloture} quand il résout l'incident.
 */
public class GererIncidents {

    static final CodeReferentiel IMPACTS = new CodeReferentiel("impact-incident");
    static final CodeReferentiel COMPOSANTS = new CodeReferentiel("composant-service");
    static final CodeReferentiel PHASES = new CodeReferentiel("phase-incident");

    public record Ouverture(Map<String, String> titre, String impact, List<String> composants, Communication communication) {
    }

    public record Communication(String phase, Map<String, String> message) {
    }

    private final Incidents incidents;
    private final ExigerValeurReferentiel referentiel;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;

    public GererIncidents(Incidents incidents, ExigerValeurReferentiel referentiel, GenerateurIdentifiant identifiants, Clock horloge) {
        this.incidents = incidents;
        this.referentiel = referentiel;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Incident ouvrir(Ouverture o) {
        referentiel.executer(IMPACTS, new CodeReferentiel(o.impact()));
        List<String> composants = o.composants() == null ? List.of() : o.composants().stream().distinct().toList();
        composants.forEach(c -> referentiel.executer(COMPOSANTS, new CodeReferentiel(c)));
        Incident incident = Incident.ouvrir(identifiants.suivant(), Libelles.de(o.titre()), o.impact(), composants, miseAJour(o.communication()),
                cloture(o.communication()));
        incidents.ajouter(incident);
        return incident;
    }

    public Incident publier(UUID id, Communication c) {
        Incident incident = incidents.parId(id).orElseThrow(IncidentIntrouvable::new);
        MiseAJourIncident m = miseAJour(c);
        Incident suite = incident.publier(m, cloture(c));
        incidents.publier(id, m, suite.fin());
        return suite;
    }

    private MiseAJourIncident miseAJour(Communication c) {
        return new MiseAJourIncident(identifiants.suivant(), c.phase(), Libelles.de(c.message()), horloge.instant());
    }

    private boolean cloture(Communication c) {
        return "true".equals(referentiel.executer(PHASES, new CodeReferentiel(c.phase())).attributs().get("cloture"));
    }
}
