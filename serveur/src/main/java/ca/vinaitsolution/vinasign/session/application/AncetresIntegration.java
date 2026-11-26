package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.application.port.OriginesIntegration;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.session.application.port.LiensSignature;
import ca.vinaitsolution.vinasign.session.domain.LienResolu;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pages autorisées à afficher l'interface du signataire dans une iframe (VS-055) : les origines déclarées par l'application
 * propriétaire de l'enveloppe, et seulement pour une session intégrée. Tout autre cas (lien d'invitation, jeton inconnu)
 * n'en autorise aucune. Le lien n'est pas ouvert : seule la page qui le porte est servie.
 */
public class AncetresIntegration {

    static final String ACTEUR = "plateforme:integration";
    private static final int JETON_MAX = 100;

    private final LiensSignature liens;
    private final LectureEnveloppes enveloppes;
    private final OriginesIntegration origines;
    private final ExecutionOrganisation execution;

    public AncetresIntegration(LiensSignature liens, LectureEnveloppes enveloppes, OriginesIntegration origines, ExecutionOrganisation execution) {
        this.liens = liens;
        this.enveloppes = enveloppes;
        this.origines = origines;
        this.execution = execution;
    }

    /** Origines triées (réponse stable) ; vide si aucune page ne peut intégrer ce lien. */
    public Set<String> pour(String jeton) {
        if (jeton == null || jeton.isBlank() || jeton.length() > JETON_MAX) {
            return Set.of();
        }
        return liens.parEmpreinte(ServicesSession.empreinte(jeton)).filter(r -> r.lien().estIntegree()).map(this::declarees).orElse(Set.of());
    }

    private Set<String> declarees(LienResolu r) {
        return execution.pour(new IdentifiantOrganisation(r.organisation()), ACTEUR, () -> enveloppes.dossier(r.lien().enveloppe())
                .map(d -> (Set<String>) new TreeSet<>(origines.de(d.application()))).orElse(Set.of()));
    }
}
