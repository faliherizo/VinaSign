package ca.vinaitsolution.vinasign.signature.application;

import ca.vinaitsolution.vinasign.signature.application.dto.RapportVerification;
import ca.vinaitsolution.vinasign.signature.application.port.Verificateur;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Vérification publique (VS-036) d'un PDF signé, ou d'une empreinte et de sa signature CMS ; aucun stockage. */
public class VerifierDocument {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};

    private final Verificateur verificateur;
    private final long tailleMax;

    public VerifierDocument(Verificateur verificateur, long tailleMax) {
        this.verificateur = verificateur;
        this.tailleMax = tailleMax;
    }

    public long tailleMax() {
        return tailleMax;
    }

    public RapportVerification pdf(byte[] contenu) {
        if (contenu == null || contenu.length < PDF.length || contenu.length > tailleMax || !java.util.Arrays.equals(contenu, 0, PDF.length, PDF, 0, PDF.length)) {
            throw new IllegalArgumentException("Un PDF (" + tailleMax + " octets au plus) est attendu.");
        }
        return verificateur.pdf(contenu);
    }

    public RapportVerification empreinte(String sha256Hex, String cmsBase64) {
        if (sha256Hex == null || !SHA256.matcher(sha256Hex).matches() || cmsBase64 == null || cmsBase64.isBlank()) {
            throw new IllegalArgumentException("Une empreinte SHA-256 (hexadécimal) et une signature CMS (base64) sont attendues.");
        }
        byte[] cms;
        try {
            cms = Base64.getDecoder().decode(cmsBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Signature CMS illisible : base64 attendu.", e);
        }
        return verificateur.empreinte(HexFormat.of().parseHex(sha256Hex), cms);
    }
}
