package ca.vinaitsolution.vinasign.referentiel.domain;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Liste métier administrable : ses valeurs vivent en base et s'ajoutent sans redéploiement. */
public record TypeReferentiel(CodeReferentiel code, Libelles libelles, List<ValeurReferentiel> valeurs) {

    public TypeReferentiel {
        if (code == null || libelles == null) {
            throw new IllegalArgumentException("Un type de référentiel a un code et des libellés.");
        }
        valeurs = valeurs == null ? List.of() : List.copyOf(valeurs);
    }

    /** Valeurs proposables, dans l'ordre d'affichage puis par code. */
    public List<ValeurReferentiel> valeursActives() {
        return valeurs.stream().filter(ValeurReferentiel::actif)
                .sorted(Comparator.comparingInt(ValeurReferentiel::ordre).thenComparing(v -> v.code().valeur()))
                .toList();
    }

    /** Valeur active portant ce code : c'est ainsi que les autres contextes valident une saisie, sans liste figée. */
    public Optional<ValeurReferentiel> valeurActive(CodeReferentiel codeValeur) {
        return valeursActives().stream().filter(v -> v.code().equals(codeValeur)).findFirst();
    }
}
