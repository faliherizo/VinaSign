package ca.vinaitsolution.vinasign.facturation;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import ca.vinaitsolution.vinasign.support.ApplicationsDeTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-121 — l'administrateur part payer un plan chez Stripe ; les événements signés de Stripe, traités une seule fois, tiennent
 * l'abonnement, le plan de l'organisation et ses factures à jour ; la fin de l'abonnement ramène le plan par défaut.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresDeTest.class, ApplicationsDeTest.class, ReferentielsSql.class})
class FacturationApiTest {

    static final StripeDeTest STRIPE = demarrer();

    static StripeDeTest demarrer() {
        try {
            return new StripeDeTest();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void stripe(DynamicPropertyRegistry r) {
        r.add("vinasign.paiement.prestataire", () -> "stripe");
        r.add("vinasign.paiement.url-api", STRIPE::url);
        r.add("vinasign.paiement.cle-secrete", () -> "sk_test_x");
        r.add("vinasign.paiement.secret-webhook", () -> StripeDeTest.SECRET_WEBHOOK);
        r.add("vinasign.paiement.prix.professionnel", () -> "price_pro");
        r.add("vinasign.paiement.prix.entreprise", () -> "price_ent");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationsDeTest applications;
    @Autowired @LiquibaseDataSource DataSource proprietaire;

    private static JwtRequestPostProcessor administrateur(UUID org) {
        return jwt().jwt(j -> j.subject("admin-" + org).claim("org", org.toString())).authorities(new SimpleGrantedAuthority("ROLE_administrateur-organisation"));
    }

    private MockHttpServletResponse evenement(String corps) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/billing/events").contentType(MediaType.APPLICATION_JSON).content(corps)
                .header("Stripe-Signature", StripeDeTest.signer(corps, Instant.now().getEpochSecond()))).andReturn().getResponse();
    }

    private String plan(UUID org) {
        return new JdbcTemplate(proprietaire).queryForObject("SELECT plan FROM organisation WHERE id = ?", String.class, org);
    }

    private JsonNode situation(UUID org) throws Exception {
        return json.readTree(mvc.perform(MockMvcRequestBuilders.get("/api/v1/billing").with(administrateur(org))).andReturn().getResponse().getContentAsString());
    }

    @Test
    void le_plan_paye_suit_les_evenements_du_prestataire() throws Exception {
        UUID org = applications.enregistrer("enveloppes-lire").organisation();
        String parDefaut = plan(org);
        assertFalse(situation(org).hasNonNull("plan"), "aucun plan payé encore");
        MockHttpServletResponse paiement = mvc.perform(MockMvcRequestBuilders.post("/api/v1/billing/checkout").with(administrateur(org))
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"professionnel\",\"langue\":\"fr\"}")).andReturn().getResponse();
        assertEquals("https://stripe.test/v1/checkout/sessions", json.readTree(paiement.getContentAsString()).get("url").asText());
        assertEquals(404, mvc.perform(MockMvcRequestBuilders.post("/api/v1/billing/portal").with(administrateur(org))).andReturn().getResponse().getStatus(),
                "aucun abonnement encore");

        String client = "cus_" + UUID.randomUUID();
        String confirme = "{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"client_reference_id\":\""
                + org + "\",\"customer\":\"" + client + "\",\"subscription\":\"sub_1\",\"metadata\":{\"plan\":\"professionnel\"}}}}";
        assertEquals(204, evenement(confirme).getStatus());
        assertEquals(204, evenement(confirme).getStatus(), "livré deux fois : traité une fois");
        assertEquals("professionnel", plan(org));
        assertEquals("actif", situation(org).get("etat").asText());

        assertEquals(204, evenement("{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"invoice.paid\",\"data\":{\"object\":{\"id\":\"in_" + UUID.randomUUID()
                + "\",\"customer\":\"" + client + "\",\"number\":\"F-001\",\"amount_paid\":4900,\"currency\":\"cad\",\"hosted_invoice_url\":\"https://h\"}}}")
                .getStatus());
        JsonNode factures = situation(org).get("factures");
        assertEquals(1, factures.size());
        assertEquals(4900, factures.get(0).get("montant").asLong());

        assertEquals(204, evenement("{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{\"id\":\"sub_1\","
                + "\"customer\":\"" + client + "\",\"status\":\"active\",\"items\":{\"data\":[{\"price\":{\"id\":\"price_ent\"}}]}}}}").getStatus());
        assertEquals("entreprise", plan(org), "changement de plan au portail du prestataire");
        assertEquals("https://stripe.test/v1/billing_portal/sessions", json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/billing/portal")
                .with(administrateur(org))).andReturn().getResponse().getContentAsString()).get("url").asText());

        assertEquals(204, evenement("{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"customer.subscription.deleted\",\"data\":{\"object\":{\"id\":\"sub_1\","
                + "\"customer\":\"" + client + "\",\"status\":\"canceled\"}}}").getStatus());
        assertEquals(parDefaut, plan(org), "abonnement terminé : plan par défaut");
        assertEquals("termine", situation(org).get("etat").asText());
    }

    @Test
    void un_evenement_non_signe_ou_un_appel_hors_administration_est_refuse() throws Exception {
        UUID org = applications.enregistrer("enveloppes-lire").organisation();
        String corps = "{\"id\":\"evt_x\",\"type\":\"invoice.paid\",\"data\":{\"object\":{}}}";
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.post("/api/v1/billing/events").contentType(MediaType.APPLICATION_JSON).content(corps)
                .header("Stripe-Signature", "t=1,v1=" + "0".repeat(64))).andReturn().getResponse().getStatus());
        assertEquals(204, evenement("{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"invoice.paid\",\"data\":{\"object\":{\"customer\":\"inconnu\"}}}")
                .getStatus(), "client inconnu : accusé, sans effet");
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.get("/api/v1/billing").with(jwt().jwt(j -> j.claim("org", org.toString()))))
                .andReturn().getResponse().getStatus());
        assertEquals(422, mvc.perform(MockMvcRequestBuilders.post("/api/v1/billing/checkout").with(administrateur(org)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"plan-inexistant\"}")).andReturn().getResponse().getStatus());
    }

    /** Chaque état de la machine d'états a son libellé au référentiel, affiché par l'écran « Facturation » du portail. */
    @Test
    void chaque_etat_d_abonnement_a_son_libelle_au_referentiel() throws Exception {
        JsonNode valeurs = json.readTree(mvc.perform(MockMvcRequestBuilders.get("/api/v1/referentiels/etat-abonnement")).andReturn().getResponse()
                .getContentAsString()).get("valeurs");
        java.util.Set<String> codes = new java.util.HashSet<>();
        valeurs.forEach(v -> codes.add(v.get("code").asText()));
        for (ca.vinaitsolution.vinasign.facturation.domain.EtatAbonnement e : ca.vinaitsolution.vinasign.facturation.domain.EtatAbonnement.values()) {
            assertTrue(codes.contains(e.code()), e.code());
        }
    }
}
