package ca.vinaitsolution.vinasign.session.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Consentement explicite d'un signataire (VS-041) : la version du texte accepté (référentiel {@code texte-consentement})
 * et l'empreinte SHA-256 du texte affiché, horodatés. Sans consentement, aucune signature.
 */
public record Consentement(UUID id, UUID enveloppe, UUID signataire, String versionTexte, String empreinteTexte, Instant accepteLe) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public Consentement {
        if (id == null || enveloppe == null || signataire == null || versionTexte == null || versionTexte.isBlank()
                || empreinteTexte == null || !SHA256.matcher(empreinteTexte).matches() || accepteLe == null) {
            throw new IllegalArgumentException("Consentement incomplet.");
        }
    }
}
