package ca.vinaitsolution.vinasign.session.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Vérification d'identité d'un signataire par un fournisseur externe (VS-131). Elle naît en cours et se conclut une seule
 * fois, réussie ou échouée ; un même résultat rappelé de nouveau par le fournisseur est sans effet, un résultat contraire est
 * refusé : le résultat consigné dans la preuve ne change jamais.
 */
public record VerificationIdentite(UUID id, UUID session, UUID signataire, String fournisseur, String reference, Etat etat, String methode,
                                   String assurance, Instant debut, Instant fin) {

    /** Machine d'états de la vérification ; ses libellés affichés viennent de l'interface. */
    public enum Etat {
        EN_COURS("en-cours"), REUSSIE("reussie"), ECHOUEE("echouee");

        private final String code;

        Etat(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Etat deCode(String code) {
            for (Etat e : values()) {
                if (e.code.equals(code)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("État de vérification inconnu : " + code);
        }
    }

    public VerificationIdentite {
        if (id == null || session == null || signataire == null || fournisseur == null || reference == null || etat == null || debut == null) {
            throw new IllegalArgumentException("Vérification d'identité incomplète.");
        }
    }

    public static VerificationIdentite demarrer(UUID id, UUID session, UUID signataire, String fournisseur, String reference, Instant le) {
        return new VerificationIdentite(id, session, signataire, fournisseur, reference, Etat.EN_COURS, null, null, le, null);
    }

    public boolean estReussie() {
        return etat == Etat.REUSSIE;
    }

    /** Résultat du fournisseur ; rejoué à l'identique : sans effet ; contraire à un résultat déjà conclu : refusé. */
    public VerificationIdentite conclure(boolean reussie, String methode, String assurance, Instant le) {
        Etat resultat = reussie ? Etat.REUSSIE : Etat.ECHOUEE;
        if (etat == resultat) {
            return this;
        }
        if (etat != Etat.EN_COURS) {
            throw new VerificationIdentiteRefusee("Résultat contraire à celui déjà consigné.");
        }
        return new VerificationIdentite(id, session, signataire, fournisseur, reference, resultat, methode, assurance, debut, le);
    }
}
