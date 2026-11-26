package ca.vinaitsolution.vinasign.signature.cle;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.application.port.CleDeSignature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import org.junit.jupiter.api.Test;

/** Contrat du port {@link CleDeSignature}, exécuté contre chaque adaptateur (LSP), erreurs comprises. */
abstract class ContratCleDeSignature {

    protected abstract CleDeSignature cle() throws Exception;

    @Test
    void la_signature_d_une_empreinte_se_verifie_avec_le_certificat_du_cachet() throws Exception {
        CleDeSignature cle = cle();
        byte[] contenu = "Document à sceller".getBytes(StandardCharsets.UTF_8);
        byte[] signature = cle.signerEmpreinte(MessageDigest.getInstance("SHA-256").digest(contenu));
        Signature verif = Signature.getInstance(cle.algorithme().nomJca());
        verif.initVerify(cle.chaine().get(0).getPublicKey());
        verif.update(contenu);
        assertTrue(verif.verify(signature));
        verif.initVerify(cle.chaine().get(0).getPublicKey());
        verif.update("Document altéré".getBytes(StandardCharsets.UTF_8));
        assertFalse(verif.verify(signature), "la signature est liée à l'empreinte");
    }

    @Test
    void seule_une_empreinte_sha256_se_signe() throws Exception {
        CleDeSignature cle = cle();
        assertThrows(IllegalArgumentException.class, () -> cle.signerEmpreinte(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> cle.signerEmpreinte("un document entier, pas une empreinte".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> cle.signerEmpreinte(null));
    }

    @Test
    void le_port_n_offre_aucun_moyen_d_obtenir_la_cle_privee() throws Exception {
        for (var m : CleDeSignature.class.getMethods()) {
            assertFalse(java.security.Key.class.isAssignableFrom(m.getReturnType()), m.getName());
        }
        assertFalse(cle().chaine().isEmpty());
    }
}
