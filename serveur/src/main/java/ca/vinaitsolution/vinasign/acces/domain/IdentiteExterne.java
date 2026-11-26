package ca.vinaitsolution.vinasign.acces.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Identité attestée par le fournisseur OIDC du portail : émetteur et sujet (stables), courriel s'il est vérifié, et
 * méthodes d'authentification ({@code amr}, RFC 8176).
 */
public record IdentiteExterne(String emetteur, String sujet, Optional<Courriel> courrielVerifie, Set<String> methodes) {

    public IdentiteExterne {
        if (emetteur == null || emetteur.isBlank() || sujet == null || sujet.isBlank()) {
            throw new IllegalArgumentException("Identité externe incomplète.");
        }
        courrielVerifie = courrielVerifie == null ? Optional.empty() : courrielVerifie;
        methodes = methodes == null ? Set.of() : Set.copyOf(methodes);
    }
}
