package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import static org.junit.jupiter.api.Assertions.*;

import java.security.MessageDigest;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.DigestCalculator;
import org.junit.jupiter.api.Test;

class EmpreinteFournieTest {

    @Test
    void la_premiere_empreinte_est_celle_fournie_les_suivantes_sont_calculees() throws Exception {
        byte[] fournie = MessageDigest.getInstance("SHA-256").digest("document jamais transmis".getBytes());
        DigestCalculator c = new EmpreinteFournie(fournie).get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
        assertEquals(NISTObjectIdentifiers.id_sha256, c.getAlgorithmIdentifier().getAlgorithm());
        assertArrayEquals(fournie, c.getDigest(), "empreinte du contenu : celle de l'application");
        c.getOutputStream().write("attributs signés".getBytes());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest("attributs signés".getBytes()), c.getDigest(), "ensuite : calculée");
    }
}
