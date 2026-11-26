package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OperationCmsTest {

    @Test
    void une_exception_controlee_devient_une_erreur_qui_nomme_l_operation_les_autres_passent_telles_quelles() {
        assertEquals(1, OperationCms.executer("Essai", () -> 1));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> OperationCms.executer("Scellement PAdES", () -> {
            throw new java.io.IOException("disque plein");
        }));
        assertEquals("Scellement PAdES impossible : disque plein", e.getMessage());
        IllegalArgumentException propre = new IllegalArgumentException("PDF illisible");
        assertSame(propre, assertThrows(IllegalArgumentException.class, () -> OperationCms.executer("Essai", () -> {
            throw propre;
        })));
    }
}
