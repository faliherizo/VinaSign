package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import java.io.OutputStream;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * Empreinte du contenu déjà calculée par l'application (VS-034, VS-ADR-005) : l'attribut messageDigest du CMS est
 * l'empreinte fournie ; les attributs signés, eux, sont hachés normalement.
 */
public final class EmpreinteFournie implements DigestCalculatorProvider {

    private static final AlgorithmIdentifier SHA256 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256);
    private final byte[] empreinte;

    public EmpreinteFournie(byte[] empreinte) {
        this.empreinte = empreinte.clone();
    }

    @Override
    public DigestCalculator get(AlgorithmIdentifier algorithme) throws OperatorCreationException {
        DigestCalculator reel = new JcaDigestCalculatorProviderBuilder().build().get(algorithme);
        return new DigestCalculator() {
            private boolean contenu = true;

            @Override
            public AlgorithmIdentifier getAlgorithmIdentifier() {
                return SHA256;
            }

            @Override
            public OutputStream getOutputStream() {
                return reel.getOutputStream();
            }

            /** Premier appel : l'empreinte du contenu (fournie) ; les suivants : celle des attributs signés (calculée). */
            @Override
            public byte[] getDigest() {
                if (contenu) {
                    contenu = false;
                    return empreinte.clone();
                }
                return reel.getDigest();
            }
        };
    }
}
