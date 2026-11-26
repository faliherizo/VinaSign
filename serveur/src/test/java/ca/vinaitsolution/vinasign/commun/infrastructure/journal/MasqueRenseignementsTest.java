package ca.vinaitsolution.vinasign.commun.infrastructure.journal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class MasqueRenseignementsTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "invitation envoyée à marie.tremblay+essai@exemple.qc.ca | invitation envoyée à [courriel]",
            "Authorization: Bearer abc.DEF-ghi_123 | Authorization: Bearer [jeton]",
            "jeton eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl reçu | jeton [jeton] reçu",
            "code SMS au 418-555-0199 | code SMS au [telephone]",
            "code SMS au +1 (418) 555 0199 | code SMS au [telephone]",
            "code SMS au 4185550199. | code SMS au [telephone].",
            "connexion password=s3cr3t, réessai | connexion password=[secret], réessai",
            "mdp: hunter2 | mdp=[secret]",
            "client_secret=abc&x=1 | client_secret=[secret]&x=1",
            "otp=482913 | otp=[secret]"
    })
    void masque_chaque_renseignement(String entree, String attendu) {
        assertEquals(attendu, MasqueRenseignements.appliquer(entree));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "enveloppe 0192f6c4-7a1b-7c3d-9e8f-123456789abc envoyée",
            "horodatage 2026-11-22T10:13:38Z",
            "référentiel code=langue chargé : 12 valeurs",
            "durée 1500 ms, 3 essais"
    })
    void laisse_intact_ce_qui_n_est_pas_personnel(String texte) {
        assertEquals(texte, MasqueRenseignements.appliquer(texte));
    }

    @Test
    void un_message_absent_reste_absent() {
        assertNull(MasqueRenseignements.appliquer(null));
    }
}
