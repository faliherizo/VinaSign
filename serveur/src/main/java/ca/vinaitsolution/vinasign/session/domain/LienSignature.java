package ca.vinaitsolution.vinasign.session.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Lien sécurisé d'un signataire invité (VS-040) : jeton de 256 bits dont seule l'empreinte est conservée, expirant,
 * révocable, à usage borné. Après la signature (ou un refus), le lien est consommé : le rejouer rend 410.
 *
 * @param utilisations nombre d'ouvertures
 */
public record LienSignature(UUID id, UUID enveloppe, UUID signataire, String empreinteJeton, Instant expireLe, int utilisations,
                            int utilisationsMax, Instant revoqueLe, Instant consommeLe, java.util.Set<String> actions, int rang) {

    public LienSignature(UUID id, UUID enveloppe, UUID signataire, String empreinteJeton, Instant expireLe, int utilisations, int utilisationsMax,
                         Instant revoqueLe, Instant consommeLe, java.util.Set<String> actions) {
        this(id, enveloppe, signataire, empreinteJeton, expireLe, utilisations, utilisationsMax, revoqueLe, consommeLe, actions, 0);
    }

    /** Lien de relance de ce rang (VS-082). */
    public LienSignature relance(int numero) {
        if (numero < 0) {
            throw new IllegalArgumentException("Rang de relance invalide.");
        }
        return new LienSignature(id, enveloppe, signataire, empreinteJeton, expireLe, utilisations, utilisationsMax, revoqueLe, consommeLe, actions, numero);
    }

    public LienSignature {
        if (id == null || enveloppe == null || signataire == null || empreinteJeton == null || expireLe == null) {
            throw new IllegalArgumentException("Lien incomplet.");
        }
        if (utilisationsMax < 1 || utilisations < 0) {
            throw new IllegalArgumentException("Usage du lien invalide.");
        }
        actions = actions == null ? java.util.Set.of() : java.util.Set.copyOf(actions);
    }

    public static LienSignature emettre(UUID id, UUID enveloppe, UUID signataire, String empreinteJeton, Instant expireLe, int utilisationsMax) {
        return new LienSignature(id, enveloppe, signataire, empreinteJeton, expireLe, 0, utilisationsMax, null, null, java.util.Set.of());
    }

    /**
     * Session intégrée (VS-046) : à usage unique, de courte durée, limitée aux actions permises (codes du référentiel
     * {@code action-signataire}). Une seconde ouverture est refusée comme un lien invalide.
     */
    public static LienSignature integree(UUID id, UUID enveloppe, UUID signataire, String empreinteJeton, Instant expireLe,
                                         java.util.Set<String> actions) {
        return new LienSignature(id, enveloppe, signataire, empreinteJeton, expireLe, 0, 1, null, null, actions);
    }

    /** Vrai pour une session intégrée (VS-046), seule à pouvoir s'afficher dans la page d'une application (VS-055). */
    public boolean estIntegree() {
        return !actions.isEmpty();
    }

    /**
     * Ouverture du lien : refusée s'il est consommé (410), révoqué, expiré ou épuisé. Le refus ne dit pas lequel :
     * aucune information n'est donnée à qui détient un lien périmé.
     */
    public LienSignature ouvrir(Instant maintenant) {
        if (consommeLe != null) {
            throw new LienConsomme();
        }
        if (revoqueLe != null || !maintenant.isBefore(expireLe) || utilisations >= utilisationsMax) {
            throw new LienInvalide();
        }
        return new LienSignature(id, enveloppe, signataire, empreinteJeton, expireLe, utilisations + 1, utilisationsMax, null, null, actions, rang);
    }

    public LienSignature revoquer(Instant le) {
        return revoqueLe != null || consommeLe != null ? this
                : new LienSignature(id, enveloppe, signataire, empreinteJeton, expireLe, utilisations, utilisationsMax, le, null, actions, rang);
    }

    /** Le signataire a agi (signé ou refusé) : le lien ne sert plus jamais. */
    public LienSignature consommer(Instant le) {
        if (consommeLe != null) {
            throw new LienConsomme();
        }
        return new LienSignature(id, enveloppe, signataire, empreinteJeton, expireLe, utilisations, utilisationsMax, revoqueLe, le, actions, rang);
    }
}
