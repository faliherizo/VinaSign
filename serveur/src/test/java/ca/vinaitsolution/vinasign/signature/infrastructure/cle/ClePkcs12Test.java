package ca.vinaitsolution.vinasign.signature.infrastructure.cle;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.cle.Certificats;
import java.nio.file.Path;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClePkcs12Test {

    @TempDir
    Path dossier;

    @Test
    void le_digest_info_fixe_est_celui_de_la_norme() throws Exception {
        byte[] sha = new byte[32];
        sha[0] = 7;
        assertArrayEquals(new DigestInfo(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE), sha).getEncoded(),
                ClePkcs12.digestInfo(sha));
    }

    @Test
    void la_trace_nomme_le_titulaire_sans_rien_reveler_de_la_cle() {
        ClePkcs12 cle = new ClePkcs12(Certificats.magasin(dossier, "RSA", "essai".toCharArray()), "essai".toCharArray());
        assertTrue(cle.toString().startsWith("ClePkcs12[CN="), cle.toString());
        assertFalse(cle.toString().contains("essai") && cle.toString().contains("Private"));
        assertThrows(IllegalStateException.class, () -> new ClePkcs12(dossier.resolve("absent.p12"), "x".toCharArray()), "magasin illisible");
    }
}
