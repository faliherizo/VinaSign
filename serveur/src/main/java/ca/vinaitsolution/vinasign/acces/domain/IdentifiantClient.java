package ca.vinaitsolution.vinasign.acces.domain;

import java.util.regex.Pattern;

/** Identifiant public d'une application cliente (OAuth 2.0 {@code client_id}) : non prévisible, jamais secret. */
public record IdentifiantClient(String valeur) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9._-]{8,80}");

    public IdentifiantClient {
        if (valeur == null || !FORMAT.matcher(valeur).matches()) {
            throw new IllegalArgumentException("Identifiant de client invalide.");
        }
    }
}
