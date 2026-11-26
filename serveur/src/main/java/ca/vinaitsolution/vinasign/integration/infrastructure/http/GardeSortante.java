package ca.vinaitsolution.vinasign.integration.infrastructure.http;

import ca.vinaitsolution.vinasign.integration.application.port.GardeDestinations;
import ca.vinaitsolution.vinasign.integration.domain.DestinationInvalide;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Garde des requêtes sortantes vers une adresse déclarée par une application (OWASP A10, SSRF ; porté de
 * {@code GardeSortante} du CLM) : HTTPS seulement (HTTP admis pour les seuls hôtes autorisés), jamais vers une adresse
 * interne (boucle locale, réseau privé, lien local dont les métadonnées infonuagiques 169.254.169.254, multidiffusion,
 * ULA, CGNAT), sauf hôte:port explicitement autorisé par la configuration (bacs à sable ; liste vide en production).
 */
class GardeSortante implements GardeDestinations {

    /** Résolution DNS injectée : les tests n'interrogent pas le réseau. */
    @FunctionalInterface
    interface Resolveur {
        InetAddress[] resoudre(String hote) throws UnknownHostException;
    }

    private final Set<String> autorises;
    private final Resolveur resolveur;

    GardeSortante(Set<String> autorises, Resolveur resolveur) {
        this.autorises = autorises.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.resolveur = resolveur;
    }

    @Override
    public void exiger(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.strip());
        } catch (IllegalArgumentException e) {
            throw new DestinationInvalide("Adresse refusée : URL invalide.");
        }
        String schema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String hote = uri.getHost();
        if (hote == null || hote.isBlank() || uri.getUserInfo() != null) {
            throw new DestinationInvalide("Adresse refusée : hôte absent ou identifiants dans l'URL.");
        }
        int port = uri.getPort() != -1 ? uri.getPort() : ("https".equals(schema) ? 443 : 80);
        boolean autorise = autorises.contains((hote + ":" + port).toLowerCase(Locale.ROOT));
        if (!"https".equals(schema) && !(autorise && "http".equals(schema))) {
            throw new DestinationInvalide("Adresse refusée : HTTPS exigé.");
        }
        if (autorise) {
            return;
        }
        for (InetAddress a : resoudre(hote)) {
            if (estInterne(a)) {
                throw new DestinationInvalide("Adresse refusée : « " + hote + " » désigne une adresse interne.");
            }
        }
    }

    private InetAddress[] resoudre(String hote) {
        try {
            return resolveur.resoudre(hote.startsWith("[") ? hote.substring(1, hote.length() - 1) : hote);
        } catch (UnknownHostException e) {
            throw new DestinationInvalide("Adresse refusée : hôte inconnu.");
        }
    }

    static boolean estInterne(InetAddress a) {
        if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] o = a.getAddress();
        boolean ula = a instanceof Inet6Address && (o[0] & 0xFE) == 0xFC;
        boolean cgnat = o.length == 4 && (o[0] & 0xFF) == 100 && (o[1] & 0xC0) == 64;
        return ula || cgnat;
    }
}
