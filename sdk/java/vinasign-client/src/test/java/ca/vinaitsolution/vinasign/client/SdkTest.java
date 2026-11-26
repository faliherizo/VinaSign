package ca.vinaitsolution.vinasign.client;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.client.genere.api.ReferentielControllerApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SdkTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant T = Instant.parse("2026-11-22T12:00:00Z");

    private HttpServer serveur;
    private final List<Map<String, String>> formulaires = new CopyOnWriteArrayList<>();
    private final List<String> autorisations = new CopyOnWriteArrayList<>();
    private final AtomicInteger statutJeton = new AtomicInteger(200);

    @BeforeEach
    void demarrer() throws Exception {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/oauth2/token", e -> {
            Map<String, String> f = new HashMap<>();
            for (String paire : new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
                String[] kv = paire.split("=", 2);
                f.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
            formulaires.add(f);
            byte[] r = ("{\"access_token\":\"jeton-" + formulaires.size() + "\",\"expires_in\":300}").getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(statutJeton.get(), r.length);
            e.getResponseBody().write(r);
            e.close();
        });
        serveur.createContext("/api/v1/referentiels", e -> {
            autorisations.add(e.getRequestHeaders().getFirst("Authorization"));
            byte[] r = "[{\"code\":\"langue\",\"libelle\":\"Langue\",\"nombreValeurs\":2}]".getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().add("Content-Type", "application/json");
            e.sendResponseHeaders(200, r.length);
            e.getResponseBody().write(r);
            e.close();
        });
        serveur.start();
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    private URI adresse() {
        return URI.create("http://127.0.0.1:" + serveur.getAddress().getPort());
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static KeyPair ec(String courbe) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec(courbe));
        return g.generateKeyPair();
    }

    /** Vérifie un JWT compact avec la clé publique, comme le ferait VinaSign ; rend ses revendications. */
    private static JsonNode verifier(String jwt, KeyPair cles) throws Exception {
        String[] p = jwt.split("\\.");
        JsonNode entete = JSON.readTree(Base64.getUrlDecoder().decode(p[0]));
        byte[] signature = Base64.getUrlDecoder().decode(p[2]);
        Signature s;
        if ("RS256".equals(entete.get("alg").asText())) {
            s = Signature.getInstance("SHA256withRSA");
        } else {
            assertEquals(64, signature.length);
            s = Signature.getInstance("SHA256withECDSAinP1363Format");
        }
        s.initVerify(cles.getPublic());
        s.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(s.verify(signature), "signature du JWT");
        return JSON.readTree(Base64.getUrlDecoder().decode(p[1]));
    }

    @Test
    void le_jeton_est_obtenu_par_assertion_signee_mis_en_cache_puis_renouvele() throws Exception {
        KeyPair cles = rsa();
        Clock[] horloge = {Clock.fixed(T, ZoneOffset.UTC)};
        Clock variable = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId z) { return this; }
            @Override public Instant instant() { return horloge[0].instant(); }
        };
        JetonsVinaSign jetons = new JetonsVinaSign(new JetonsVinaSign.Identite(adresse(), "https://auth.vinasign.ca", "app-1", cles.getPrivate(), "k1",
                List.of("enveloppes-lire", "enveloppes-creer")), HttpClient.newHttpClient(), variable);
        assertEquals("jeton-1", jetons.jeton());
        assertEquals("jeton-1", jetons.jeton(), "en cache");
        Map<String, String> f = formulaires.get(0);
        assertEquals("client_credentials", f.get("grant_type"));
        assertEquals("enveloppes-lire enveloppes-creer", f.get("scope"));
        JsonNode assertion = verifier(f.get("client_assertion"), cles);
        assertEquals("app-1", assertion.get("iss").asText());
        assertEquals("app-1", assertion.get("sub").asText());
        assertEquals("https://auth.vinasign.ca", assertion.get("aud").asText());
        assertEquals(60, assertion.get("exp").asLong() - assertion.get("iat").asLong());
        horloge[0] = Clock.fixed(T.plusSeconds(271), ZoneOffset.UTC);
        assertEquals("jeton-2", jetons.jeton(), "renouvelé 30 s avant l'échéance");
        statutJeton.set(401);
        horloge[0] = Clock.fixed(T.plusSeconds(10_000), ZoneOffset.UTC);
        assertThrows(JetonsVinaSign.JetonRefuse.class, jetons::jeton);
        JetonsVinaSign injoignable = new JetonsVinaSign(new JetonsVinaSign.Identite(URI.create("http://127.0.0.1:1"), "e", "app", cles.getPrivate(), null,
                List.of()), HttpClient.newHttpClient(), variable);
        assertThrows(JetonsVinaSign.JetonRefuse.class, injoignable::jeton);
    }

    @Test
    void les_api_generees_portent_le_jeton_de_l_application() throws Exception {
        VinaSign vinaSign = new VinaSign(new JetonsVinaSign.Identite(adresse(), "https://auth.vinasign.ca", "app-1", rsa().getPrivate(), null, List.of()),
                Clock.systemUTC());
        var referentiels = new ReferentielControllerApi(vinaSign.apiClient()).referentielLister(null);
        assertEquals("langue", referentiels.get(0).getCode());
        assertEquals(List.of("Bearer jeton-1"), autorisations);
        assertNotNull(vinaSign.jetons());
    }

    @Test
    void les_assertions_es256_sont_au_format_jose_et_les_cles_inadaptees_refusees() throws Exception {
        KeyPair cles = ec("secp256r1");
        for (int i = 0; i < 20; i++) {
            JsonNode r = verifier(JwtSigne.signer(cles.getPrivate(), "k", Map.of("n", i)), cles);
            assertEquals(i, r.get("n").asInt());
        }
        assertThrows(IllegalArgumentException.class, () -> JwtSigne.signer(ec("secp384r1").getPrivate(), null, Map.of()));
        KeyPairGenerator dsa = KeyPairGenerator.getInstance("DSA");
        dsa.initialize(2048);
        assertThrows(IllegalArgumentException.class, () -> JwtSigne.signer(dsa.generateKeyPair().getPrivate(), null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> JwtSigne.signer(cles.getPrivate(), null, Map.of("x", new Object())));
    }

    @Test
    void l_attestation_designe_l_utilisateur_et_sa_seconde_preuve() throws Exception {
        KeyPair cles = rsa();
        String jwt = new Attestations("https://auth.vinasign.ca", "app-1", cles.getPrivate(), Clock.fixed(T, ZoneOffset.UTC))
                .attester("medecin-42", List.of("pwd", "otp"), T.minusSeconds(60));
        JsonNode r = verifier(jwt, cles);
        assertEquals("https://auth.vinasign.ca/attestations", r.get("aud").asText());
        assertEquals("medecin-42", r.get("sub").asText());
        assertEquals("otp", r.get("amr").get(1).asText());
        assertEquals(T.minusSeconds(60).getEpochSecond(), r.get("auth_time").asLong());
    }

    @Test
    void le_webhook_se_verifie_comme_le_serveur_le_signe() {
        // Vecteur de référence partagé avec le serveur (WebhookDomaineTest) : HMAC-SHA256("secret-de-test", "1000.{"id":"1"}").
        String entete = "t=1000,v1=666c03ec35508aac04dd952534a53765722de9e065b99131f1ad2fa957180969";
        byte[] corps = "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8);
        VinaSignWebhooks w = new VinaSignWebhooks("secret-de-test", Clock.fixed(Instant.ofEpochSecond(1200), ZoneOffset.UTC), Duration.ofMinutes(5));
        assertTrue(w.verifier(entete, corps));
        assertFalse(w.verifier(entete, "{}".getBytes()));
        assertFalse(w.verifier(entete.replace("t=1000", "t=1001"), corps));
        assertFalse(w.verifier(null, corps));
        assertFalse(w.verifier("v1=00", corps));
        assertFalse(new VinaSignWebhooks("secret-de-test", Clock.fixed(Instant.ofEpochSecond(2000), ZoneOffset.UTC), Duration.ofMinutes(5))
                .verifier(entete, corps), "rejeu ancien");
    }

    @Test
    void une_meme_note_donne_la_meme_empreinte_quel_que_soit_le_poste() {
        String mac = "Note\r\nCafé  \r\n\r\n";
        String linux = "Note\nCafé\n";
        assertEquals("Note\nCafé", Empreintes.canonique(mac));
        assertArrayEquals(Empreintes.texte(mac), Empreintes.texte(linux));
        assertEquals(32, Empreintes.sha256(new byte[0]).length);
        assertEquals("a\nb", Empreintes.canonique("a\rb\t"));
        assertThrows(IllegalStateException.class, () -> Empreintes.condenser("SHA-INEXISTANT", new byte[0]));
        assertThrows(IllegalStateException.class, () -> VinaSignWebhooks.hmac("HmacInexistant", new byte[1], 1, new byte[0]));
    }

    @Test
    void une_cle_inutilisable_ou_une_attente_interrompue_sont_signalees() throws Exception {
        java.security.interfaces.RSAPrivateKey invalide = new java.security.interfaces.RSAPrivateKey() {
            @Override public java.math.BigInteger getPrivateExponent() { return java.math.BigInteger.ONE; }
            @Override public String getAlgorithm() { return "RSA"; }
            @Override public String getFormat() { return null; }
            @Override public byte[] getEncoded() { return null; }
            @Override public java.math.BigInteger getModulus() { return java.math.BigInteger.TEN; }
        };
        assertThrows(IllegalStateException.class, () -> JwtSigne.signer(invalide, null, Map.of()));
        JetonsVinaSign jetons = new JetonsVinaSign(new JetonsVinaSign.Identite(adresse(), "e", "app", rsa().getPrivate(), null, List.of()),
                HttpClient.newHttpClient(), Clock.systemUTC());
        Thread.currentThread().interrupt();
        try {
            assertThrows(JetonsVinaSign.JetonRefuse.class, jetons::jeton);
            assertTrue(Thread.currentThread().isInterrupted(), "l'interruption est conservée");
        } finally {
            Thread.interrupted();
        }
    }
}
