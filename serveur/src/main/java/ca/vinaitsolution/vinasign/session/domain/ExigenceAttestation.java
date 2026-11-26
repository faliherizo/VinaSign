package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.Attestation;
import ca.vinaitsolution.vinasign.commun.domain.AttestationRefusee;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Ce que l'attestation d'un utilisateur de l'application doit établir pour ouvrir sa session intégrée (VS-044,
 * VS-ADR-007) : qu'il s'agit bien du signataire (sujet externe), et, au niveau renforcé ou au-delà, une seconde preuve
 * récente parmi les méthodes reconnues comme fortes.
 *
 * @param rangRenforce  rang à partir duquel une seconde preuve est exigée (attribut « rang » du référentiel)
 * @param methodesFortes méthodes {@code amr} qui valent seconde preuve (ex. otp, hwk, mfa)
 * @param fraicheur      âge maximal de cette preuve
 */
public record ExigenceAttestation(int rangRenforce, Set<String> methodesFortes, Duration fraicheur) {

    public ExigenceAttestation {
        methodesFortes = Set.copyOf(methodesFortes);
        if (rangRenforce < 1 || methodesFortes.isEmpty() || fraicheur.isNegative() || fraicheur.isZero()) {
            throw new IllegalArgumentException("Exigence d'attestation incohérente.");
        }
    }

    public void verifier(Attestation a, String sujetAttendu, int rangDemande, Instant maintenant) {
        if (sujetAttendu == null || !sujetAttendu.equals(a.sujet())) {
            throw new AttestationRefusee("Le sujet de l'attestation ne désigne pas ce signataire.");
        }
        if (rangDemande < rangRenforce) {
            return;
        }
        if (a.methodes().stream().noneMatch(methodesFortes::contains)) {
            throw new AttestationRefusee("Niveau renforcé : une seconde preuve (amr) est exigée.");
        }
        if (a.authentifieLe() == null || a.authentifieLe().isBefore(maintenant.minus(fraicheur)) || a.authentifieLe().isAfter(maintenant.plusSeconds(30))) {
            throw new AttestationRefusee("Niveau renforcé : l'authentification attestée (auth_time) doit dater de moins de "
                    + fraicheur.toMinutes() + " minutes.");
        }
    }
}
