package ca.vinaitsolution.vinasign.etat.domain;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Incident publié sur la page d'état (VS-124) : titre par langue, impact (référentiel impact-incident), composants
 * touchés (référentiel composant-service), communications successives. Une communication de clôture le résout ; un
 * incident résolu est figé.
 */
public record Incident(UUID id, Libelles titre, String impact, List<String> composants, Instant debut, Instant fin,
                       List<MiseAJourIncident> misesAJour) {

    public Incident {
        if (id == null || impact == null || impact.isBlank() || debut == null) {
            throw new IllegalArgumentException("Incident incomplet.");
        }
        exigerTexte(titre, 200, "Le titre");
        composants = composants == null ? List.of() : List.copyOf(composants);
        if (composants.isEmpty()) {
            throw new IncidentInvalide("Nommer au moins un composant touché.");
        }
        misesAJour = misesAJour == null ? List.of() : List.copyOf(misesAJour);
        if (fin != null && fin.isBefore(debut)) {
            throw new IllegalArgumentException("Fin antérieure au début.");
        }
    }

    /** Nouvel incident, ouvert par sa première communication. */
    public static Incident ouvrir(UUID id, Libelles titre, String impact, List<String> composants, MiseAJourIncident premiere, boolean cloture) {
        return new Incident(id, titre, impact, composants, premiere.le(), null, List.of()).publier(premiere, cloture);
    }

    public boolean estOuvert() {
        return fin == null;
    }

    /** Ajoute une communication ; {@code cloture} : la phase publiée résout l'incident. */
    public Incident publier(MiseAJourIncident m, boolean cloture) {
        if (!estOuvert()) {
            throw new IncidentClos();
        }
        List<MiseAJourIncident> suite = new ArrayList<>(misesAJour);
        suite.add(m);
        return new Incident(id, titre, impact, composants, debut, cloture ? m.le() : null, suite);
    }

    static void exigerTexte(Libelles texte, int max, String quoi) {
        if (texte == null || texte.parLangue().isEmpty()) {
            throw new IncidentInvalide(quoi + " est requis dans au moins une langue.");
        }
        if (texte.parLangue().values().stream().anyMatch(t -> t.length() > max)) {
            throw new IncidentInvalide(quoi + " dépasse " + max + " caractères.");
        }
    }
}
