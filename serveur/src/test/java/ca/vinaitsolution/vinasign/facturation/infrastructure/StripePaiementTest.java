package ca.vinaitsolution.vinasign.facturation.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.facturation.StripeDeTest;
import ca.vinaitsolution.vinasign.facturation.application.port.PrestatairePaiement;
import ca.vinaitsolution.vinasign.facturation.domain.EtatAbonnement;
import ca.vinaitsolution.vinasign.facturation.domain.EvenementPaiement;
import ca.vinaitsolution.vinasign.facturation.domain.PaiementIndisponible;
import ca.vinaitsolution.vinasign.facturation.domain.PaiementRefuse;
import ca.vinaitsolution.vinasign.facturation.infrastructure.stripe.PaiementAucun;
import ca.vinaitsolution.vinasign.facturation.infrastructure.stripe.StripePaiement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** VS-121 — Stripe : page hébergée par plan, événements crus seulement signés et récents, traduits dans le vocabulaire du domaine. */
class StripePaiementTest {

    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");
    private static final UUID ORG = UUID.randomUUID();

    private static StripePaiement stripe(String url) {
        return new StripePaiement(new StripePaiement.Acces(url, "sk_test_x", StripeDeTest.SECRET_WEBHOOK),
                new StripePaiement.Catalogue(Map.of("professionnel", "price_pro", "entreprise", "price_ent"), "https://portail/facturation"),
                Clock.fixed(T, ZoneOffset.UTC), new ObjectMapper());
    }

    private static EvenementPaiement lire(StripePaiement s, String corps) throws Exception {
        return s.lire(corps.getBytes(StandardCharsets.UTF_8), StripeDeTest.signer(corps, T.getEpochSecond()));
    }

    @Test
    void la_page_hebergee_porte_le_prix_du_plan_et_la_reference_de_l_organisation() throws Exception {
        try (StripeDeTest faux = new StripeDeTest()) {
            StripePaiement s = stripe(faux.url());
            assertEquals("stripe", s.code());
            assertEquals("https://stripe.test/v1/checkout/sessions", s.ouvrirPaiement(new PrestatairePaiement.Souscription(ORG, "professionnel", null, "fr")));
            String f = faux.formulaires.get(0);
            assertTrue(f.contains("line_items%5B0%5D%5Bprice%5D=price_pro") && f.contains("client_reference_id=" + ORG) && f.contains("locale=fr")
                    && !f.contains("customer=") && f.endsWith("|auth=Bearer sk_test_x"), f);
            s.ouvrirPaiement(new PrestatairePaiement.Souscription(ORG, "entreprise", "cus_1", null));
            assertTrue(faux.formulaires.get(1).contains("customer=cus_1") && faux.formulaires.get(1).contains("locale=auto"));
            assertEquals("https://stripe.test/v1/billing_portal/sessions", s.ouvrirGestion("cus_1"));
            assertThrows(PaiementIndisponible.class, () -> s.ouvrirPaiement(new PrestatairePaiement.Souscription(ORG, "essentiel", null, "fr")),
                    "plan sans prix en ligne");
            faux.statut = 402;
            assertThrows(PaiementIndisponible.class, () -> s.ouvrirGestion("cus_1"));
        }
        String ferme;
        try (StripeDeTest f = new StripeDeTest()) {
            ferme = f.url();
        }
        assertThrows(PaiementIndisponible.class, () -> stripe(ferme).ouvrirGestion("cus_1"), "prestataire injoignable");
    }

    @Test
    void les_evenements_suivis_sont_traduits() throws Exception {
        StripePaiement s = stripe("http://inutile");
        assertEquals(new EvenementPaiement.PaiementConfirme("evt_1", ORG, "cus_1", "sub_1", "professionnel"), lire(s,
                "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"client_reference_id\":\"" + ORG
                        + "\",\"customer\":\"cus_1\",\"subscription\":\"sub_1\",\"metadata\":{\"plan\":\"professionnel\"}}}}"));
        assertEquals(new EvenementPaiement.AbonnementModifie("evt_2", "cus_1", "sub_1", "entreprise", EtatAbonnement.ACTIF), lire(s,
                "{\"id\":\"evt_2\",\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{\"id\":\"sub_1\",\"customer\":\"cus_1\","
                        + "\"status\":\"active\",\"items\":{\"data\":[{\"price\":{\"id\":\"price_ent\"}}]}}}}"));
        EvenementPaiement.AbonnementModifie inconnu = (EvenementPaiement.AbonnementModifie) lire(s,
                "{\"id\":\"evt_3\",\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{\"customer\":\"cus_1\",\"status\":\"past_due\"}}}");
        assertNull(inconnu.plan(), "prix inconnu de la configuration");
        assertEquals(EtatAbonnement.IMPAYE, inconnu.etat());
        assertEquals(EtatAbonnement.TERMINE, ((EvenementPaiement.AbonnementModifie) lire(s,
                "{\"id\":\"evt_4\",\"type\":\"customer.subscription.deleted\",\"data\":{\"object\":{\"customer\":\"cus_1\",\"status\":\"active\"}}}")).etat());
        EvenementPaiement.FacturePayee f = (EvenementPaiement.FacturePayee) lire(s, "{\"id\":\"evt_5\",\"type\":\"invoice.paid\",\"data\":{\"object\":{"
                + "\"id\":\"in_1\",\"customer\":\"cus_1\",\"number\":\"F-001\",\"amount_paid\":4900,\"currency\":\"cad\",\"hosted_invoice_url\":\"https://h\","
                + "\"invoice_pdf\":\"https://p\",\"period_start\":1700000000,\"period_end\":1702592000,\"status_transitions\":{\"paid_at\":1700000100}}}}");
        assertEquals(new EvenementPaiement.FacturePayee("evt_5", "cus_1", "in_1", "F-001", 4900, "CAD", "https://h", "https://p",
                Instant.ofEpochSecond(1700000000), Instant.ofEpochSecond(1702592000), Instant.ofEpochSecond(1700000100)), f);
        EvenementPaiement.FacturePayee sansDate = (EvenementPaiement.FacturePayee) lire(s,
                "{\"id\":\"evt_6\",\"type\":\"invoice.paid\",\"data\":{\"object\":{\"id\":\"in_2\",\"customer\":\"cus_1\",\"amount_paid\":0}}}");
        assertEquals(T, sansDate.payeeLe());
        assertEquals("CAD", sansDate.devise());
        assertNull(sansDate.debutPeriode());
        assertEquals(new EvenementPaiement.Ignore("evt_7"), lire(s, "{\"id\":\"evt_7\",\"type\":\"customer.created\",\"data\":{\"object\":{}}}"));
        for (String statut : new String[] {"trialing", "canceled", "incomplete_expired", "unpaid"}) {
            assertNotNull(lire(s, "{\"id\":\"e\",\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{\"status\":\"" + statut + "\"}}}"));
        }
    }

    @Test
    void un_evenement_non_signe_ancien_ou_illisible_est_refuse() throws Exception {
        StripePaiement s = stripe("http://inutile");
        byte[] corps = "{\"id\":\"e\"}".getBytes(StandardCharsets.UTF_8);
        String bonne = StripeDeTest.signer("{\"id\":\"e\"}", T.getEpochSecond());
        assertNotNull(s.lire(corps, bonne));
        for (String entete : new String[] {null, "", "v1=abc", "t=abc," + bonne.split(",")[1], "t=" + (T.getEpochSecond() - 600) + "," + bonne.split(",")[1],
                "t=" + T.getEpochSecond() + ",v1=" + "0".repeat(64), "t=" + T.getEpochSecond() + ",v1=zz"}) {
            assertThrows(PaiementRefuse.class, () -> s.lire(corps, entete), String.valueOf(entete));
        }
        assertThrows(PaiementRefuse.class, () -> lire(s, "pas du json"));
        assertThrows(PaiementRefuse.class, () -> lire(s, "{\"id\":\"e\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{}}}"),
                "paiement sans référence d'organisation");
    }

    @Test
    void une_configuration_incomplete_est_refusee_et_sans_prestataire_rien_ne_se_paie() {
        ObjectMapper j = new ObjectMapper();
        StripePaiement.Catalogue c = new StripePaiement.Catalogue(Map.of(), "https://x");
        assertThrows(IllegalStateException.class, () -> new StripePaiement(new StripePaiement.Acces("u", null, "w"), c, Clock.systemUTC(), j));
        assertThrows(IllegalStateException.class, () -> new StripePaiement(new StripePaiement.Acces("u", " ", "w"), c, Clock.systemUTC(), j));
        assertThrows(IllegalStateException.class, () -> new StripePaiement(new StripePaiement.Acces("u", "k", null), c, Clock.systemUTC(), j));
        assertThrows(IllegalStateException.class, () -> new StripePaiement(new StripePaiement.Acces("u", "k", ""), c, Clock.systemUTC(), j));
        PaiementAucun aucun = new PaiementAucun();
        assertEquals("aucun", aucun.code());
        assertThrows(PaiementIndisponible.class, () -> aucun.ouvrirPaiement(new PrestatairePaiement.Souscription(ORG, "p", null, "fr")));
        assertThrows(PaiementIndisponible.class, () -> aucun.ouvrirGestion("c"));
        assertThrows(PaiementRefuse.class, () -> aucun.lire(new byte[0], "s"));
    }
}
