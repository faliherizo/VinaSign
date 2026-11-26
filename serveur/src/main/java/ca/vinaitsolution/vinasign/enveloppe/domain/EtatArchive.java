package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * État archivé du document final (VS-132) : le final lui-même, puis chaque renouvellement de son horodatage. L'échéance
 * est celle du dernier horodatage ; nulle, le document n'en porte aucun et ne se renouvelle pas.
 */
public record EtatArchive(UUID id, IdentifiantEnveloppe enveloppe, String cleObjet, String sha256, long taille, Instant echeance, Instant creeLe) {

    /** Rend ces octets s'ils sont exactement ceux de l'état archivé, sinon {@link EmpreinteAlteree}. */
    public byte[] verifier(byte[] contenu) {
        if (!ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256Hex(contenu).equals(sha256)) {
            throw new EmpreinteAlteree();
        }
        return contenu;
    }

    /** Vrai si l'échéance tombe avant cette limite : il faut renouveler maintenant. */
    public boolean echoitAvant(Instant limite) {
        return echeance != null && echeance.isBefore(limite);
    }
}
