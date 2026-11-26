package ca.vinaitsolution.vinasign.signature.cle;

import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import ca.vinaitsolution.vinasign.signature.infrastructure.cle.ClePkcs12;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

class ClesPkcs12ContratTest {

    @TempDir
    static Path dossier;

    @Nested
    class Rsa extends ContratCleDeSignature {
        @Override
        protected CleDeSignature cle() {
            return new ClePkcs12(Certificats.magasin(dossier, "RSA", "essai".toCharArray()), "essai".toCharArray());
        }
    }

    @Nested
    class Ec extends ContratCleDeSignature {
        @Override
        protected CleDeSignature cle() {
            return new ClePkcs12(Certificats.magasin(dossier, "EC", "essai".toCharArray()), "essai".toCharArray());
        }
    }
}
