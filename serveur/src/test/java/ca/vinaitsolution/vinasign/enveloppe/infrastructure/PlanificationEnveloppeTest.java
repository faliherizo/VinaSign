package ca.vinaitsolution.vinasign.enveloppe.infrastructure;

import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.enveloppe.application.AncrerJournal;
import ca.vinaitsolution.vinasign.enveloppe.application.ExpirerEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.FinaliserEnveloppes;
import org.junit.jupiter.api.Test;

class PlanificationEnveloppeTest {

    @Test
    void les_echeances_declenchent_expiration_finalisation_et_ancrage() {
        ExpirerEnveloppes expiration = mock(ExpirerEnveloppes.class);
        FinaliserEnveloppes finalisation = mock(FinaliserEnveloppes.class);
        AncrerJournal ancrage = mock(AncrerJournal.class);
        ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives archivage = mock(ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives.class);
        new EnveloppeConfiguration.ExpirationPlanifiee(expiration).expirer();
        new EnveloppeConfiguration.FinalisationPlanifiee(finalisation).finaliser();
        new EnveloppeConfiguration.AncragePlanifie(ancrage).ancrer();
        new EnveloppeConfiguration.ArchivagePlanifie(archivage).renouveler();
        verify(archivage).executer();
        verify(expiration).executer();
        verify(finalisation).executer();
        verify(ancrage).executer();
    }
}
