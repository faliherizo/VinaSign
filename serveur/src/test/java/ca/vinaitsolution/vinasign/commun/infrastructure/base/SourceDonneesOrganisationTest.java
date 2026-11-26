package ca.vinaitsolution.vinasign.commun.infrastructure.base;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SourceDonneesOrganisationTest {

    @Test
    void l_organisation_est_posee_a_chaque_emprunt_y_compris_avec_identifiants() throws SQLException {
        DataSource cible = mock(DataSource.class);
        Connection c = connexion();
        when(cible.getConnection()).thenReturn(c);
        when(cible.getConnection("u", "p")).thenReturn(c);
        IdentifiantOrganisation org = new IdentifiantOrganisation(UUID.randomUUID());
        SourceDonneesOrganisation source = new SourceDonneesOrganisation(cible, () -> Optional.of(org));
        assertSame(c, source.getConnection());
        assertSame(c, source.getConnection("u", "p"));
        verify(c.prepareStatement(anyString()), times(2)).setString(1, org.valeur().toString());
    }

    @Test
    void sans_organisation_le_parametre_est_vide() throws SQLException {
        DataSource cible = mock(DataSource.class);
        Connection c = connexion();
        when(cible.getConnection()).thenReturn(c);
        new SourceDonneesOrganisation(cible, Optional::empty).getConnection();
        verify(c.prepareStatement(anyString())).setString(1, "");
    }

    @Test
    void une_connexion_dont_l_organisation_ne_peut_etre_posee_est_fermee_et_jamais_rendue() throws SQLException {
        DataSource cible = mock(DataSource.class);
        Connection c = connexion();
        when(c.prepareStatement(anyString()).execute()).thenThrow(new SQLException("panne"));
        when(cible.getConnection()).thenReturn(c);
        SourceDonneesOrganisation source = new SourceDonneesOrganisation(cible, Optional::empty);
        assertThrows(SQLException.class, source::getConnection);
        verify(c).close();
    }

    @Test
    void le_mot_de_passe_n_apparait_jamais_dans_la_description_des_reglages() {
        assertFalse(new BaseProprietes("vinasign_application", "secret-tres-long").toString().contains("secret"));
    }

    private static Connection connexion() throws SQLException {
        Connection c = mock(Connection.class);
        PreparedStatement p = mock(PreparedStatement.class);
        when(c.prepareStatement(anyString())).thenReturn(p);
        return c;
    }
}
