package ca.vinaitsolution.vinasign.acces.domain;

import java.util.Set;

/**
 * Jeu de clés publiques vérifié d'une application (JWKS) et les empreintes de ses clés (RFC 7638) : une clé n'appartient
 * qu'à une application, ce qui sépare aussi le bac à sable de la production.
 */
public record ClesPubliques(String jwks, Set<String> empreintes) {

    public ClesPubliques {
        if (jwks == null || jwks.isBlank() || empreintes == null || empreintes.isEmpty()) {
            throw new IllegalArgumentException("Clés publiques vides.");
        }
        empreintes = Set.copyOf(empreintes);
    }
}
