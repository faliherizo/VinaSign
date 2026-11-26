package ca.vinaitsolution.vinasign.support;

import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Double de test du port, soumis au même test de contrat que l'implémentation PostgreSQL. */
public class ReferentielsEnMemoire implements Referentiels {

    private final List<TypeReferentiel> types = new ArrayList<>();

    public ReferentielsEnMemoire avec(TypeReferentiel type) {
        types.add(type);
        return this;
    }

    @Override
    public List<TypeReferentiel> types() {
        return List.copyOf(types);
    }

    @Override
    public Optional<TypeReferentiel> type(CodeReferentiel code) {
        return types.stream().filter(t -> t.code().equals(code)).findFirst();
    }
}
