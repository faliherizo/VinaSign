package ca.vinaitsolution.vinasign.integration.infrastructure;

import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.integration.application.LivrerWebhooks;
import org.junit.jupiter.api.Test;

class PlanificationIntegrationTest {

    @Test
    void l_echeance_declenche_la_livraison_des_webhooks() {
        LivrerWebhooks livraison = mock(LivrerWebhooks.class);
        new IntegrationConfiguration.LivraisonPlanifiee(livraison).livrer();
        verify(livraison).executer();
    }
}
