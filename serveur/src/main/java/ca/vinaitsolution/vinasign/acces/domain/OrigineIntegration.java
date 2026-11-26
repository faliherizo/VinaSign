package ca.vinaitsolution.vinasign.acces.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Origine autorisée à intégrer l'interface du signataire en iframe (VS-011, VS-055) : {@code https://hôte[:port]},
 * sans chemin, requête, fragment ni identifiants. Elle alimente {@code frame-ancestors} et le contrôle d'origine des
 * messages {@code postMessage} : une valeur approximative ouvrirait l'intégration à un tiers.
 */
public record OrigineIntegration(String valeur) {

    public OrigineIntegration {
        valeur = normaliser(valeur);
    }

    private static String normaliser(String texte) {
        URI uri;
        try {
            uri = new URI(texte == null ? "" : texte.strip());
        } catch (URISyntaxException e) {
            throw invalide();
        }
        boolean cheminVide = uri.getRawPath() == null || uri.getRawPath().isEmpty();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || !cheminVide
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalide();
        }
        String hote = uri.getHost().toLowerCase(Locale.ROOT);
        return uri.getPort() == -1 || uri.getPort() == 443 ? "https://" + hote : "https://" + hote + ":" + uri.getPort();
    }

    private static IllegalArgumentException invalide() {
        return new IllegalArgumentException("Origine d'intégration invalide : https://hôte[:port], sans chemin.");
    }
}
