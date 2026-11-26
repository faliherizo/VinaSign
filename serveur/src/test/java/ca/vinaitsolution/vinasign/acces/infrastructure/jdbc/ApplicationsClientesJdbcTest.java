package ca.vinaitsolution.vinasign.acces.infrastructure.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationsClientesJdbcTest {

    @Test
    void une_ligne_revoquee_est_relue_avec_sa_date_de_revocation() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Array portees = mock(Array.class);
        Instant le = Instant.parse("2026-11-22T12:00:00Z");
        when(portees.getArray()).thenReturn(new String[]{"enveloppes-lire"});
        when(rs.getArray("portees")).thenReturn(portees);
        Array origines = mock(Array.class);
        when(origines.getArray()).thenReturn(new String[]{"https://rh.exemple.ca"});
        when(rs.getArray("origines_integration")).thenReturn(origines);
        when(rs.getTimestamp("revoque_le")).thenReturn(Timestamp.from(le));
        when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getObject("tenant_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getString("client_id")).thenReturn("essai-12345678");
        when(rs.getString("nom")).thenReturn("Système");
        when(rs.getString("environnement")).thenReturn("production");
        when(rs.getString("jwks")).thenReturn("{\"keys\":[]}");
        when(rs.getString("statut")).thenReturn("revoquee");
        ApplicationCliente a = ApplicationsClientesJdbc.application(rs);
        assertFalse(a.active());
        assertEquals(le, a.revoqueLe());
        assertEquals("https://rh.exemple.ca", a.origines().iterator().next().valeur());
    }
}
