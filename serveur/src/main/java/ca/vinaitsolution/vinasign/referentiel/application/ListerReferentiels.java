package ca.vinaitsolution.vinasign.referentiel.application;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.referentiel.application.dto.ResumeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import java.util.Comparator;
import java.util.List;

/** Cas d'utilisation : découvrir les référentiels disponibles (l'interface et les tests partent de là, jamais d'une liste). */
public class ListerReferentiels {

    private final Referentiels referentiels;
    private final CodeLangue repli;

    public ListerReferentiels(Referentiels referentiels, CodeLangue repli) {
        this.referentiels = referentiels;
        this.repli = repli;
    }

    public List<ResumeReferentiel> executer(CodeLangue langue) {
        return referentiels.types().stream()
                .map(t -> new ResumeReferentiel(t.code().valeur(), t.libelles().pour(langue, repli, t.code().valeur()),
                        t.valeursActives().size()))
                .sorted(Comparator.comparing(ResumeReferentiel::code))
                .toList();
    }
}
