package ca.vinaitsolution.vinasign.etat.application;

import ca.vinaitsolution.vinasign.etat.application.dto.VueEtat;
import ca.vinaitsolution.vinasign.etat.application.port.Incidents;
import ca.vinaitsolution.vinasign.etat.domain.Incident;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Page d'état (VS-124) : l'impact d'un composant est le plus grave des incidents ouverts qui le touchent (gravité = ordre
 * du référentiel impact-incident ; le premier est l'état normal) ; l'impact global, le plus grave de tous. L'historique
 * couvre la période configurée.
 */
public class ConsulterEtat {

    private final Incidents incidents;
    private final Referentiels referentiels;
    private final Clock horloge;
    private final Duration historique;

    public ConsulterEtat(Incidents incidents, Referentiels referentiels, Clock horloge, Duration historique) {
        this.incidents = incidents;
        this.referentiels = referentiels;
        this.horloge = horloge;
        this.historique = historique;
    }

    public VueEtat consulter() {
        Instant maintenant = horloge.instant();
        List<ValeurReferentiel> impacts = actives(GererIncidents.IMPACTS);
        if (impacts.isEmpty()) {
            throw new IllegalStateException("Référentiel impact-incident vide.");
        }
        Map<String, Integer> gravite = impacts.stream().collect(Collectors.toMap(v -> v.code().valeur(), impacts::indexOf));
        String normal = impacts.get(0).code().valeur();
        List<Incident> liste = incidents.depuis(maintenant.minus(historique));
        Map<String, String> composants = new LinkedHashMap<>();
        for (ValeurReferentiel c : actives(GererIncidents.COMPOSANTS)) {
            composants.put(c.code().valeur(), pire(liste.stream().filter(i -> i.estOuvert() && i.composants().contains(c.code().valeur())).toList(),
                    gravite, normal));
        }
        String global = pire(liste.stream().filter(Incident::estOuvert).toList(), gravite, normal);
        return new VueEtat(global, composants, liste.stream().map(ConsulterEtat::vue).toList(), maintenant);
    }

    private List<ValeurReferentiel> actives(CodeReferentiel type) {
        return referentiels.type(type).map(TypeReferentiel::valeursActives).orElse(List.of());
    }

    private static String pire(List<Incident> ouverts, Map<String, Integer> gravite, String normal) {
        return ouverts.stream().map(Incident::impact).max((a, b) -> Integer.compare(gravite.getOrDefault(a, 0), gravite.getOrDefault(b, 0)))
                .orElse(normal);
    }

    private static VueEtat.VueIncident vue(Incident i) {
        return new VueEtat.VueIncident(i.id(), textes(i.titre()), i.impact(), i.composants(), i.debut(), i.fin(),
                i.misesAJour().stream().map(m -> new VueEtat.VueMiseAJour(m.phase(), textes(m.message()), m.le())).toList());
    }

    private static Map<String, String> textes(ca.vinaitsolution.vinasign.commun.domain.Libelles l) {
        Map<String, String> m = new LinkedHashMap<>();
        l.parLangue().forEach((langue, texte) -> m.put(langue.valeur(), texte));
        return m;
    }
}
