package ca.vinaitsolution.vinasign.facturation;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.facturation.domain.Abonnement;
import ca.vinaitsolution.vinasign.facturation.domain.EtatAbonnement;
import ca.vinaitsolution.vinasign.facturation.domain.Facture;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** VS-121 — le plan suit l'abonnement : appliqué s'il est actif, par défaut s'il est terminé, inchangé pendant un impayé. */
class FacturationDomaineTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");
    private static final UUID ORG = UUID.randomUUID();

    @Test
    void le_plan_suit_l_etat_de_l_abonnement() {
        Abonnement a = new Abonnement(ORG, "stripe", "cus_1", "sub_1", "professionnel", EtatAbonnement.ACTIF, T);
        assertEquals(Abonnement.EffetPlan.APPLIQUER, a.effetPlan());
        Abonnement impaye = a.evoluer(null, null, EtatAbonnement.IMPAYE, T.plusSeconds(1));
        assertEquals(Abonnement.EffetPlan.AUCUN, impaye.effetPlan());
        assertEquals("sub_1", impaye.reference(), "référence inconnue : gardée");
        assertEquals("professionnel", impaye.plan(), "plan inconnu de la configuration : gardé");
        Abonnement change = a.evoluer("sub_2", "entreprise", EtatAbonnement.TERMINE, T.plusSeconds(2));
        assertEquals(Abonnement.EffetPlan.PAR_DEFAUT, change.effetPlan());
        assertEquals("entreprise", change.plan());
        assertEquals("sub_2", change.reference());
        for (EtatAbonnement e : EtatAbonnement.values()) {
            assertEquals(e, EtatAbonnement.deCode(e.code()));
        }
        assertThrows(IllegalArgumentException.class, () -> EtatAbonnement.deCode("x"));
    }

    @Test
    void un_abonnement_ou_une_facture_incomplets_sont_refuses() {
        assertThrows(IllegalArgumentException.class, () -> new Abonnement(null, "s", "c", null, "p", EtatAbonnement.ACTIF, T));
        assertThrows(IllegalArgumentException.class, () -> new Abonnement(ORG, null, "c", null, "p", EtatAbonnement.ACTIF, T));
        assertThrows(IllegalArgumentException.class, () -> new Abonnement(ORG, "s", null, null, "p", EtatAbonnement.ACTIF, T));
        assertThrows(IllegalArgumentException.class, () -> new Abonnement(ORG, "s", "c", null, null, EtatAbonnement.ACTIF, T));
        assertThrows(IllegalArgumentException.class, () -> new Abonnement(ORG, "s", "c", null, "p", null, T));
        assertThrows(IllegalArgumentException.class, () -> new Abonnement(ORG, "s", "c", null, "p", EtatAbonnement.ACTIF, null));
        UUID id = UUID.randomUUID();
        assertEquals(1500, new Facture(id, ORG, "in_1", "F-1", 1500, "CAD", null, null, null, null, T).montant());
        assertThrows(IllegalArgumentException.class, () -> new Facture(null, ORG, "in", null, 1, "CAD", null, null, null, null, T));
        assertThrows(IllegalArgumentException.class, () -> new Facture(id, null, "in", null, 1, "CAD", null, null, null, null, T));
        assertThrows(IllegalArgumentException.class, () -> new Facture(id, ORG, null, null, 1, "CAD", null, null, null, null, T));
        assertThrows(IllegalArgumentException.class, () -> new Facture(id, ORG, "in", null, 1, null, null, null, null, null, T));
        assertThrows(IllegalArgumentException.class, () -> new Facture(id, ORG, "in", null, 1, "CAD", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Facture(id, ORG, "in", null, -1, "CAD", null, null, null, null, T));
    }
}
