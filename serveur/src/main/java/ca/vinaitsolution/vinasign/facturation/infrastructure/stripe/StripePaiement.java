package ca.vinaitsolution.vinasign.facturation.infrastructure.stripe;

import ca.vinaitsolution.vinasign.commun.domain.OperationCryptographique;
import ca.vinaitsolution.vinasign.facturation.application.port.PrestatairePaiement;
import ca.vinaitsolution.vinasign.facturation.domain.EtatAbonnement;
import ca.vinaitsolution.vinasign.facturation.domain.EvenementPaiement;
import ca.vinaitsolution.vinasign.facturation.domain.PaiementIndisponible;
import ca.vinaitsolution.vinasign.facturation.domain.PaiementRefuse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stripe (VS-121) : Checkout hébergé en mode abonnement, portail client hébergé, webhooks signés (en-tête
 * {@code Stripe-Signature : t=…,v1=…}, HMAC-SHA256 de {@code t.corps}, tolérance de cinq minutes). Appels REST directs,
 * formulaire encodé, clé secrète par l'environnement ; chaque prix Stripe correspond à un plan par configuration.
 */
public class StripePaiement implements PrestatairePaiement {

    /** Accès : adresse de l'API (configurable pour les essais), clé secrète, secret des webhooks. */
    public record Acces(String urlApi, String cleSecrete, String secretWebhook) {
    }

    /** Correspondance plan → prix Stripe, et pages de retour du portail. */
    public record Catalogue(Map<String, String> prix, String urlRetour) {
    }

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final Acces acces;
    private final Catalogue catalogue;
    private final Clock horloge;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public StripePaiement(Acces acces, Catalogue catalogue, Clock horloge, ObjectMapper json) {
        if (acces.cleSecrete() == null || acces.cleSecrete().isBlank() || acces.secretWebhook() == null || acces.secretWebhook().isBlank()) {
            throw new IllegalStateException("Stripe : clé secrète et secret des webhooks requis (environnement ou coffre).");
        }
        this.acces = acces;
        this.catalogue = catalogue;
        this.horloge = horloge;
        this.json = json;
    }

    @Override
    public String code() {
        return "stripe";
    }

    @Override
    public String ouvrirPaiement(Souscription s) {
        String prix = catalogue.prix().get(s.plan());
        if (prix == null || prix.isBlank()) {
            throw new PaiementIndisponible("Le plan « " + s.plan() + " » ne se paie pas en ligne : communiquez avec nous.");
        }
        Map<String, String> f = new LinkedHashMap<>();
        f.put("mode", "subscription");
        f.put("line_items[0][price]", prix);
        f.put("line_items[0][quantity]", "1");
        f.put("client_reference_id", s.organisation().toString());
        f.put("metadata[plan]", s.plan());
        f.put("subscription_data[metadata][organisation]", s.organisation().toString());
        f.put("locale", s.langue() == null ? "auto" : s.langue());
        f.put("success_url", catalogue.urlRetour() + "?paiement=reussi");
        f.put("cancel_url", catalogue.urlRetour() + "?paiement=annule");
        if (s.client() != null) {
            f.put("customer", s.client());
        }
        return poster("/v1/checkout/sessions", f).path("url").asText();
    }

    @Override
    public String ouvrirGestion(String client) {
        return poster("/v1/billing_portal/sessions", Map.of("customer", client, "return_url", catalogue.urlRetour())).path("url").asText();
    }

    @Override
    public EvenementPaiement lire(byte[] corps, String signature) {
        verifier(corps, signature);
        JsonNode e;
        try {
            e = json.readTree(corps);
        } catch (IOException ex) {
            throw new PaiementRefuse("Événement illisible.");
        }
        String id = e.path("id").asText("");
        JsonNode o = e.path("data").path("object");
        return switch (e.path("type").asText("")) {
            case "checkout.session.completed" -> new EvenementPaiement.PaiementConfirme(id, organisation(o),
                    o.path("customer").asText(), texte(o, "subscription"), o.path("metadata").path("plan").asText());
            case "customer.subscription.updated", "customer.subscription.deleted" -> new EvenementPaiement.AbonnementModifie(id, o.path("customer").asText(),
                    texte(o, "id"), planDuPrix(o.path("items").path("data").path(0).path("price").path("id").asText("")), etat(e.path("type").asText(), o));
            case "invoice.paid" -> new EvenementPaiement.FacturePayee(id, o.path("customer").asText(), o.path("id").asText(), texte(o, "number"),
                    o.path("amount_paid").asLong(), o.path("currency").asText("cad").toUpperCase(java.util.Locale.ROOT), texte(o, "hosted_invoice_url"),
                    texte(o, "invoice_pdf"), instant(o.path("period_start")), instant(o.path("period_end")),
                    o.path("status_transitions").path("paid_at").isNumber() ? instant(o.path("status_transitions").path("paid_at")) : horloge.instant());
            default -> new EvenementPaiement.Ignore(id);
        };
    }

    /** {@code t=…,v1=…} : horodatage dans la tolérance et une signature v1 égale, en temps constant, au HMAC attendu. */
    private void verifier(byte[] corps, String entete) {
        if (entete == null) {
            throw new PaiementRefuse("Signature absente.");
        }
        Map<String, java.util.List<String>> parties = java.util.Arrays.stream(entete.split(",")).map(p -> p.split("=", 2)).filter(p -> p.length == 2)
                .collect(Collectors.groupingBy(p -> p[0].strip(), Collectors.mapping(p -> p[1].strip(), Collectors.toList())));
        String t = parties.getOrDefault("t", java.util.List.of("")).get(0);
        if (!t.matches("\\d{1,12}") || Duration.between(Instant.ofEpochSecond(Long.parseLong(t)), horloge.instant()).abs().compareTo(TOLERANCE) > 0) {
            throw new PaiementRefuse("Horodatage de l'événement absent ou hors tolérance.");
        }
        byte[] attendu = hmac((t + ".").getBytes(StandardCharsets.UTF_8), corps);
        boolean valide = parties.getOrDefault("v1", java.util.List.of()).stream().filter(v -> v.matches("[0-9a-f]{64}"))
                .anyMatch(v -> MessageDigest.isEqual(attendu, HexFormat.of().parseHex(v)));
        if (!valide) {
            throw new PaiementRefuse("Signature de l'événement invalide.");
        }
    }

    private byte[] hmac(byte[] prefixe, byte[] corps) {
        return OperationCryptographique.executer("Signature Stripe", () -> {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(acces.secretWebhook().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(prefixe);
            return mac.doFinal(corps);
        });
    }

    private JsonNode poster(String chemin, Map<String, String> formulaire) {
        String corps = formulaire.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        HttpRequest r = HttpRequest.newBuilder(URI.create(acces.urlApi() + chemin)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + acces.cleSecrete()).header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString(corps)).build();
        try {
            HttpResponse<String> reponse = http.send(r, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() / 100 != 2) {
                throw new PaiementIndisponible("Le prestataire de paiement a refusé la demande (HTTP " + reponse.statusCode() + ").");
            }
            return json.readTree(reponse.body());
        } catch (IOException e) {
            throw new PaiementIndisponible("Prestataire de paiement injoignable.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaiementIndisponible("Appel au prestataire interrompu.");
        }
    }

    /** Référence posée au départ du paiement : l'identifiant de l'organisation. */
    private static UUID organisation(JsonNode session) {
        try {
            return UUID.fromString(session.path("client_reference_id").asText(""));
        } catch (IllegalArgumentException e) {
            throw new PaiementRefuse("Paiement sans référence d'organisation.");
        }
    }

    private String planDuPrix(String prix) {
        return catalogue.prix().entrySet().stream().filter(e -> e.getValue().equals(prix)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /** Vocabulaire Stripe → état du domaine ; un abonnement supprimé est terminé. */
    static EtatAbonnement etat(String type, JsonNode abonnement) {
        if (type.endsWith(".deleted")) {
            return EtatAbonnement.TERMINE;
        }
        return switch (abonnement.path("status").asText("")) {
            case "active", "trialing" -> EtatAbonnement.ACTIF;
            case "canceled", "incomplete_expired" -> EtatAbonnement.TERMINE;
            default -> EtatAbonnement.IMPAYE;
        };
    }

    private static String texte(JsonNode n, String champ) {
        String v = n.path(champ).asText("");
        return v.isEmpty() ? null : v;
    }

    private static Instant instant(JsonNode secondes) {
        return secondes.isNumber() ? Instant.ofEpochSecond(secondes.asLong()) : null;
    }
}
