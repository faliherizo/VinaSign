package ca.vinaitsolution.vinasign.referentiel.application;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.referentiel.application.dto.VueReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.dto.VueValeur;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ReferentielInconnu;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;

/** Cas d'utilisation : les valeurs actives d'un référentiel, libellées, pour remplir une liste de l'interface. */
public class ConsulterReferentiel {

    private final Referentiels referentiels;
    private final CodeLangue repli;

    public ConsulterReferentiel(Referentiels referentiels, CodeLangue repli) {
        this.referentiels = referentiels;
        this.repli = repli;
    }

    public VueReferentiel executer(CodeReferentiel code, CodeLangue langue) {
        TypeReferentiel type = referentiels.type(code).orElseThrow(() -> new ReferentielInconnu(code));
        return new VueReferentiel(code.valeur(), type.libelles().pour(langue, repli, code.valeur()), langue.valeur(),
                type.valeursActives().stream()
                        .map(v -> new VueValeur(v.code().valeur(), v.libelles().pour(langue, repli, v.code().valeur()),
                                v.ordre(), v.attributs()))
                        .toList());
    }
}
