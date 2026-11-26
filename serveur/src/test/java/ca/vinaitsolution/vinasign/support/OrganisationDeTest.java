package ca.vinaitsolution.vinasign.support;

import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Optional;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

/** Organisation courante pilotée par le test (remplace celle du jeton dans les tests d'isolation). */
@TestComponent
@Primary
public class OrganisationDeTest implements OrganisationCourante {

    private volatile IdentifiantOrganisation courante;

    public void agirPour(IdentifiantOrganisation organisation) {
        this.courante = organisation;
    }

    public void anonyme() {
        this.courante = null;
    }

    @Override
    public Optional<IdentifiantOrganisation> courante() {
        return Optional.ofNullable(courante);
    }
}
