package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Version immuable d'un document (VS-022) : original déposé, préparé, final. Son empreinte SHA-256 est calculée au
 * dépôt et revérifiée à chaque lecture critique ; une réécriture est refusée.
 *
 * @param nature  code du référentiel {@code nature-document}
 * @param cleObjet clé de l'objet dans le stockage
 */
public record VersionDocument(UUID id, IdentifiantEnveloppe enveloppe, String nature, String cleObjet, String sha256, long taille,
                              int pages, Instant creeLe) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public VersionDocument {
        if (id == null || enveloppe == null || nature == null || nature.isBlank() || cleObjet == null || cleObjet.isBlank()
                || sha256 == null || !SHA256.matcher(sha256).matches() || taille < 1 || pages < 1 || creeLe == null) {
            throw new IllegalArgumentException("Version de document incomplète.");
        }
    }

    /** Les octets relus du stockage, s'ils sont exactement ceux de cette version ; {@link EmpreinteAlteree} sinon. */
    public byte[] verifier(byte[] contenu) {
        if (!correspond(ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256(contenu))) {
            throw new EmpreinteAlteree();
        }
        return contenu;
    }

    /** Vrai si ces octets sont exactement ceux de la version ; l'empreinte se compare en temps constant. */
    public boolean correspond(byte[] empreinte) {
        return java.security.MessageDigest.isEqual(HexFormat.of().parseHex(sha256), empreinte);
    }
}
