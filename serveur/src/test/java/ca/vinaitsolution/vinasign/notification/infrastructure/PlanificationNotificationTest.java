package ca.vinaitsolution.vinasign.notification.infrastructure;

import static org.mockito.Mockito.*;

import ca.vinaitsolution.vinasign.notification.application.ExpedierCourriels;
import ca.vinaitsolution.vinasign.notification.application.ExpedierSms;
import org.junit.jupiter.api.Test;

/** Chaque échéance planifiée déclenche l'expédition correspondante, et elle seule. */
class PlanificationNotificationTest {

    @Test
    void les_echeances_declenchent_l_expedition_des_courriels_et_des_sms() {
        ExpedierCourriels courriels = mock(ExpedierCourriels.class);
        ExpedierSms sms = mock(ExpedierSms.class);
        new NotificationConfiguration.ExpeditionPlanifiee(courriels).expedier();
        new NotificationConfiguration.ExpeditionSmsPlanifiee(sms).expedier();
        verify(courriels).executer();
        verify(sms).executer();
        verifyNoMoreInteractions(courriels, sms);
    }
}
