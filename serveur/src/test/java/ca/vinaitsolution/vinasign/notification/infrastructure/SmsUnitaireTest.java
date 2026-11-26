package ca.vinaitsolution.vinasign.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.infrastructure.ChiffrementAesGcm;
import ca.vinaitsolution.vinasign.notification.application.ExpedierSms;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteSms;
import ca.vinaitsolution.vinasign.notification.infrastructure.NotificationConfiguration.NotificationProprietes;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SmsUnitaireTest {

    private static final Instant T0 = Instant.parse("2026-11-23T01:00:00Z");
    private static final String CLE = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void le_chiffrement_est_reversible_aleatoire_et_detecte_une_alteration() {
        ChiffrementAesGcm c = new ChiffrementAesGcm(CLE, new SecureRandom());
        byte[] un = c.chiffrer("+14185550199");
        assertEquals("+14185550199", c.dechiffrer(un));
        assertFalse(java.util.Arrays.equals(un, c.chiffrer("+14185550199")), "IV aléatoire : deux chiffrés différents");
        un[un.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> c.dechiffrer(un));
        byte[] autre = new ChiffrementAesGcm(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32}), new SecureRandom()).chiffrer("x");
        assertThrows(IllegalStateException.class, () -> c.dechiffrer(autre), "autre clé");
        assertThrows(IllegalStateException.class, () -> c.dechiffrer(new byte[3]));
        assertThrows(IllegalStateException.class, () -> new ChiffrementAesGcm(null, new SecureRandom()));
        assertThrows(IllegalStateException.class, () -> new ChiffrementAesGcm(Base64.getEncoder().encodeToString(new byte[16]), new SecureRandom()));
    }

    /** Boîte en mémoire qui consigne les décisions. */
    private static final class Boite implements BoiteSms {
        final List<SmsDu> dus = new ArrayList<>();
        final List<String> decisions = new ArrayList<>();

        public void deposer(UUID id, UUID organisation, String numero, String texte, Instant prochaine) {
            dus.add(new SmsDu(id, numero, texte, 0));
        }

        public List<SmsDu> dus(Instant maintenant, int limite) {
            return dus;
        }

        public void envoye(UUID id, Instant le) {
            decisions.add("envoye");
        }

        public void echec(UUID id, int tentatives, Instant prochaine, boolean abandonne) {
            decisions.add("echec:" + tentatives + ":" + prochaine + ":" + abandonne);
        }
    }

    @Test
    void un_echec_de_sms_est_repris_puis_abandonne() {
        Boite b = new Boite();
        b.deposer(UUID.randomUUID(), UUID.randomUUID(), "+14185550199", "code", T0);
        List<String> envoyes = new ArrayList<>();
        assertEquals(1, new ExpedierSms(b, (n, t) -> envoyes.add(n), Clock.fixed(T0, ZoneOffset.UTC), 10, 3).executer());
        assertEquals(List.of("+14185550199"), envoyes);
        Boite echec = new Boite();
        echec.dus.add(new BoiteSms.SmsDu(UUID.randomUUID(), "+1", "t", 2));
        new ExpedierSms(echec, (n, t) -> { throw new IllegalStateException(); }, Clock.fixed(T0, ZoneOffset.UTC), 10, 3).executer();
        assertEquals("echec:3:" + T0.plusSeconds(240) + ":true", echec.decisions.get(0));
        assertFalse(new BoiteSms.SmsDu(UUID.randomUUID(), "+14185550199", "t", 0).toString().contains("4185550199"));
    }

    @Test
    void le_simulateur_est_refuse_en_production_et_sans_fournisseur_l_envoi_echoue_explicitement() {
        NotificationConfiguration c = new NotificationConfiguration();
        assertThrows(IllegalStateException.class,
                () -> c.sms(new NotificationProprietes("https://s", "e@x.ca", 1, 1, "simulateur", "production"), null));
        assertThrows(IllegalStateException.class,
                () -> c.sms(new NotificationProprietes("https://s", "e@x.ca", 1, 1, null, "poste"), null).envoyer("+1", "t"));
    }
}
