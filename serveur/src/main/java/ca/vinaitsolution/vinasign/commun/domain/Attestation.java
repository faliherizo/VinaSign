package ca.vinaitsolution.vinasign.commun.domain;

import java.time.Instant;
import java.util.List;

/**
 * Ce qu'une application cliente atteste de son utilisateur (VS-044), une fois la signature de l'assertion vérifiée :
 * son identifiant chez elle, ses méthodes d'authentification ({@code amr}, RFC 8176) et leur heure ({@code auth_time}).
 */
public record Attestation(String sujet, List<String> methodes, Instant authentifieLe) {

    public Attestation {
        methodes = methodes == null ? List.of() : List.copyOf(methodes);
    }
}
