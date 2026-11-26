package ca.vinaitsolution.vinasign.integration.infrastructure.http;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.integration.application.port.EnvoiWebhook;
import ca.vinaitsolution.vinasign.integration.domain.DestinationInvalide;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SortantTest {

    private static GardeSortante garde(String ip) {
        return new GardeSortante(Set.of("Recepteur.Local:8080"), h -> {
            if (h.equals("inconnu.exemple")) throw new UnknownHostException(h);
            return new InetAddress[] {InetAddress.getByName(ip)};
        });
    }

    @Test
    void seules_les_adresses_https_publiques_ou_autorisees_passent() throws Exception {
        garde("203.0.113.10").exiger("https://rh.exemple.ca/webhooks");
        garde("10.0.0.1").exiger("http://recepteur.local:8080/h");
        for (String ip : new String[] {"127.0.0.1", "10.1.2.3", "192.168.0.4", "172.16.0.1", "169.254.169.254", "0.0.0.0", "224.0.0.1", "100.64.0.1",
                "::1", "fc00::1", "fe80::1"}) {
            assertThrows(DestinationInvalide.class, () -> garde(ip).exiger("https://rh.exemple.ca/h"), ip);
        }
        assertFalse(GardeSortante.estInterne(InetAddress.getByAddress(new byte[] {100, (byte) 128, 0, 1})), "hors CGNAT");
        assertFalse(GardeSortante.estInterne(InetAddressPublique.ipv6()), "IPv6 publique");
        for (String url : new String[] {"http://rh.exemple.ca/h", "ftp://rh.exemple.ca/h", "https:///h", "https://u:p@rh.exemple.ca/h", "pas une url ^", "",
                "http://recepteur.local:9090/h"}) {
            assertThrows(DestinationInvalide.class, () -> garde("203.0.113.10").exiger(url), url);
        }
        assertThrows(DestinationInvalide.class, () -> garde("203.0.113.10").exiger(null));
        assertThrows(DestinationInvalide.class, () -> garde("203.0.113.10").exiger("https://inconnu.exemple/h"));
        assertThrows(DestinationInvalide.class, () -> garde("::1").exiger("https://[::1]/h"));
        assertNotNull(Sortant.garde(Set.of()));
    }

    @Test
    void l_envoi_rend_le_code_ne_suit_aucune_redirection_et_signale_l_injoignable() throws Exception {
        HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/ok", e -> { e.sendResponseHeaders(204, -1); e.close(); });
        serveur.createContext("/ailleurs", e -> { e.getResponseHeaders().add("Location", "http://169.254.169.254/"); e.sendResponseHeaders(302, -1); e.close(); });
        serveur.start();
        try {
            EnvoiWebhook envoi = Sortant.envoi(Duration.ofSeconds(2));
            String base = "http://127.0.0.1:" + serveur.getAddress().getPort();
            assertEquals(204, envoi.envoyer(base + "/ok", Map.of("X-Essai", "1"), "{}".getBytes()));
            assertEquals(302, envoi.envoyer(base + "/ailleurs", Map.of(), "{}".getBytes()), "redirection non suivie");
        } finally {
            serveur.stop(0);
        }
        EnvoiWebhook envoi = Sortant.envoi(Duration.ofSeconds(1));
        assertThrows(EnvoiWebhook.Injoignable.class, () -> envoi.envoyer("http://127.0.0.1:1/h", Map.of(), new byte[0]));
        assertThrows(EnvoiWebhook.Injoignable.class, () -> envoi.envoyer("http://127.0.0.1:1/h", Map.of("Host", "interdit"), new byte[0]),
                "en-tête restreint");
        Thread.currentThread().interrupt();
        try {
            // Adresse non routable : la connexion est en attente quand l'interruption est constatée.
            assertThrows(EnvoiWebhook.Injoignable.class, () -> envoi.envoyer("http://10.255.255.1/h", Map.of(), new byte[0]), "attente interrompue");
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /** Adresse IPv6 publique de documentation, sans résolution. */
    static final class InetAddressPublique {
        static InetAddress ipv6() throws UnknownHostException {
            return InetAddress.getByName("2001:db8::1");
        }
    }
}
