package ca.vinaitsolution.vinasign.commun.infrastructure.base;

import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Source de données de l'application (VS-005, VS-ADR-006). Le pool se connecte sous le rôle applicatif lui-même, qui
 * n'est ni propriétaire des tables ni exempté de la RLS et ne peut endosser aucun autre rôle. À chaque emprunt, elle
 * pose l'organisation courante dans {@code app.organisation}, ou la vide : une connexion rendue au pool ne transporte
 * jamais l'organisation de la requête précédente.
 */
public class SourceDonneesOrganisation extends DelegatingDataSource {

    static final String PARAMETRE = "app.organisation";

    private final OrganisationCourante organisation;

    public SourceDonneesOrganisation(DataSource cible, OrganisationCourante organisation) {
        super(cible);
        this.organisation = organisation;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return preparer(super.getConnection());
    }

    @Override
    public Connection getConnection(String utilisateur, String motDePasse) throws SQLException {
        return preparer(super.getConnection(utilisateur, motDePasse));
    }

    private Connection preparer(Connection connexion) throws SQLException {
        try (PreparedStatement parametre = connexion.prepareStatement("SELECT set_config('" + PARAMETRE + "', ?, false)")) {
            parametre.setString(1, organisation.courante().map(IdentifiantOrganisation::valeur).map(Object::toString).orElse(""));
            parametre.execute();
            return connexion;
        } catch (SQLException e) {
            connexion.close();
            throw e;
        }
    }
}
