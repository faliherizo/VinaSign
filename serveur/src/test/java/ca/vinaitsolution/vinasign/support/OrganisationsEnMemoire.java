package ca.vinaitsolution.vinasign.support;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.port.Organisations;
import ca.vinaitsolution.vinasign.organisation.domain.Organisation;
import ca.vinaitsolution.vinasign.organisation.domain.OrganisationIntrouvable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Double du port {@link Organisations}, soumis au même contrat que l'adaptateur JDBC. */
public class OrganisationsEnMemoire implements Organisations {

    private final Map<IdentifiantOrganisation, Organisation> parId = new ConcurrentHashMap<>();

    @Override
    public void ajouter(Organisation o) {
        if (parId.putIfAbsent(o.id(), o) != null) {
            throw new IllegalStateException("Organisation déjà présente.");
        }
    }

    @Override
    public void remplacer(Organisation o) {
        if (parId.replace(o.id(), o) == null) {
            throw new OrganisationIntrouvable();
        }
    }

    @Override
    public Optional<Organisation> parId(IdentifiantOrganisation id) {
        return Optional.ofNullable(parId.get(id));
    }
}
