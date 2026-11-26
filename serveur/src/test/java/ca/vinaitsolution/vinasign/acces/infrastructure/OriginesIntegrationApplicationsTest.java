package ca.vinaitsolution.vinasign.acces.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OriginesIntegrationApplicationsTest {

    @Test
    void sans_application_designee_aucune_origine_et_aucune_lecture() {
        ApplicationsClientes applications = mock(ApplicationsClientes.class);
        OriginesIntegrationApplications origines = new OriginesIntegrationApplications(applications);
        assertEquals(Set.of(), origines.de(null));
        assertEquals(Set.of(), origines.de(" "));
        verifyNoInteractions(applications);
        when(applications.activeParClient(any())).thenReturn(Optional.empty());
        assertEquals(Set.of(), origines.de("inconnue"));
    }
}
