package ca.vinaitsolution.vinasign.integration.infrastructure.http;

import ca.vinaitsolution.vinasign.integration.application.port.EnvoiWebhook;
import ca.vinaitsolution.vinasign.integration.application.port.GardeDestinations;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Set;

/** Fabrique des adaptateurs HTTP sortants (classes du paquet non publiques). */
public final class Sortant {

    private Sortant() {
    }

    public static GardeDestinations garde(Set<String> autorises) {
        return new GardeSortante(autorises, InetAddress::getAllByName);
    }

    public static EnvoiWebhook envoi(Duration delai) {
        return new EnvoiHttp(delai);
    }
}
