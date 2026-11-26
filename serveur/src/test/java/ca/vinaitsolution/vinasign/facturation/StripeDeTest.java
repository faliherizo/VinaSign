package ca.vinaitsolution.vinasign.facturation;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Faux Stripe local (VS-121) : rend une URL de session, retient les formulaires reçus ; signe les événements comme Stripe. */
public final class StripeDeTest implements AutoCloseable {

    public static final String SECRET_WEBHOOK = "whsec_test_secret";
    private final HttpServer serveur;
    public final List<String> formulaires = new CopyOnWriteArrayList<>();
    public volatile int statut = 200;

    public StripeDeTest() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/v1/", ex -> {
            formulaires.add(ex.getRequestURI().getPath() + "?" + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    + "|auth=" + ex.getRequestHeaders().getFirst("Authorization"));
            byte[] r = ("{\"url\":\"https://stripe.test" + ex.getRequestURI().getPath() + "\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(statut, r.length);
            ex.getResponseBody().write(r);
            ex.close();
        });
        serveur.start();
    }

    public String url() {
        return "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    /** En-tête Stripe-Signature de ce corps à cet instant (secondes). */
    public static String signer(String corps, long t) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET_WEBHOOK.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + t + ",v1=" + java.util.HexFormat.of().formatHex(mac.doFinal((t + "." + corps).getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void close() {
        serveur.stop(0);
    }
}
