package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

/**
 * Signataire Bouncy Castle adossé au port {@link CleDeSignature} : il calcule l'empreinte SHA-256 des attributs signés
 * et ne transmet qu'elle à la clé (HSM) ; la clé privée ne quitte jamais son support.
 */
final class SignataireCle implements ContentSigner {

    private final CleDeSignature cle;
    private final ByteArrayOutputStream donnees = new ByteArrayOutputStream();

    SignataireCle(CleDeSignature cle) {
        this.cle = cle;
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return new DefaultSignatureAlgorithmIdentifierFinder().find(cle.algorithme().nomJca());
    }

    @Override
    public OutputStream getOutputStream() {
        return donnees;
    }

    @Override
    public byte[] getSignature() {
        return cle.signerEmpreinte(ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256(donnees.toByteArray()));
    }
}
