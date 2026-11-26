package ca.vinaitsolution.vinasign.referentiel;

import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({PostgresDeTest.class, ReferentielsSql.class})
class ReferentielsJdbcContratTest extends ContratReferentiels {

    @Autowired Referentiels referentiels;
    @Autowired ReferentielsSql sql;

    @Override
    protected Referentiels referentiels() {
        return referentiels;
    }

    @Override
    protected void enregistrer(TypeReferentiel type) {
        sql.enregistrer(type);
    }
}
