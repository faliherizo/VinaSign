package ca.vinaitsolution.vinasign.session;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.Attestation;
import ca.vinaitsolution.vinasign.commun.domain.AttestationRefusee;
import ca.vinaitsolution.vinasign.session.domain.ExigenceAttestation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExigenceAttestationTest {

    private static final Instant T = Instant.parse("2026-11-22T15:00:00Z");
    private final ExigenceAttestation exigence = new ExigenceAttestation(2, Set.of("otp", "hwk", "mfa"), Duration.ofMinutes(5));

    private static Attestation de(String sujet, List<String> amr, Instant le) {
        return new Attestation(sujet, amr, le);
    }

    @Test
    void le_sujet_atteste_doit_etre_celui_du_signataire() {
        exigence.verifier(de("medecin-42", List.of("pwd"), null), "medecin-42", 1, T);
        AttestationRefusee e = assertThrows(AttestationRefusee.class, () -> exigence.verifier(de("medecin-43", List.of("pwd"), T), "medecin-42", 1, T));
        assertEquals("attestation-refusee", e.type());
        assertThrows(AttestationRefusee.class, () -> exigence.verifier(de(null, List.of(), T), "medecin-42", 1, T));
        assertThrows(AttestationRefusee.class, () -> exigence.verifier(de("medecin-42", List.of(), T), null, 1, T), "signataire sans sujet externe");
    }

    @Test
    void au_niveau_renforce_une_seconde_preuve_recente_est_exigee() {
        exigence.verifier(de("u", List.of("pwd", "otp"), T.minus(Duration.ofMinutes(5))), "u", 2, T);
        exigence.verifier(de("u", List.of("hwk"), T.plusSeconds(30)), "u", 3, T);
        assertThrows(AttestationRefusee.class, () -> exigence.verifier(de("u", List.of("pwd"), T), "u", 2, T), "mot de passe seul");
        assertThrows(AttestationRefusee.class, () -> exigence.verifier(de("u", List.of("mfa"), null), "u", 2, T), "sans auth_time");
        assertThrows(AttestationRefusee.class, () -> exigence.verifier(de("u", List.of("mfa"), T.minus(Duration.ofMinutes(5)).minusSeconds(1)), "u", 2, T), "trop ancienne");
        AttestationRefusee futur = assertThrows(AttestationRefusee.class, () -> exigence.verifier(de("u", List.of("mfa"), T.plusSeconds(31)), "u", 2, T));
        assertTrue(futur.getMessage().contains("5 minutes"));
    }

    @Test
    void une_attestation_retenue_est_complete_et_figee() {
        java.util.UUID lien = java.util.UUID.randomUUID();
        var r = new ca.vinaitsolution.vinasign.session.domain.AttestationRetenue(lien, "u", new java.util.ArrayList<>(List.of("otp")), null, "renforce", T);
        assertThrows(UnsupportedOperationException.class, () -> r.methodes().add("x"));
        assertThrows(IllegalArgumentException.class, () -> new ca.vinaitsolution.vinasign.session.domain.AttestationRetenue(null, "u", List.of(), T, "n", T));
        assertThrows(IllegalArgumentException.class, () -> new ca.vinaitsolution.vinasign.session.domain.AttestationRetenue(lien, null, List.of(), T, "n", T));
        assertThrows(IllegalArgumentException.class, () -> new ca.vinaitsolution.vinasign.session.domain.AttestationRetenue(lien, "u", List.of(), T, null, T));
        assertThrows(IllegalArgumentException.class, () -> new ca.vinaitsolution.vinasign.session.domain.AttestationRetenue(lien, "u", List.of(), T, "n", null));
    }

    @Test
    void une_exigence_incoherente_est_refusee_et_les_listes_sont_figees() {
        assertThrows(IllegalArgumentException.class, () -> new ExigenceAttestation(0, Set.of("otp"), Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class, () -> new ExigenceAttestation(2, Set.of(), Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class, () -> new ExigenceAttestation(2, Set.of("otp"), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ExigenceAttestation(2, Set.of("otp"), Duration.ofMinutes(-1)));
        assertThrows(UnsupportedOperationException.class, () -> exigence.methodesFortes().add("pwd"));
        assertEquals(List.of(), new Attestation("u", null, T).methodes());
        assertThrows(UnsupportedOperationException.class, () -> new Attestation("u", new java.util.ArrayList<>(List.of("otp")), T).methodes().add("x"));
    }
}
