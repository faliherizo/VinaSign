package ca.vinaitsolution.vinasign.commun.domain;

import java.util.Optional;

/**
 * Ce qu'une requête authentifiée engage : l'organisation au nom de laquelle elle agit et, pour une application cliente,
 * son identifiant de client. Vide pour une requête anonyme.
 */
public record ContexteAcces(Optional<IdentifiantOrganisation> organisation, Optional<String> client) {

    /**
     * Espace des enveloppes préparées au portail par ses utilisateurs (VS-ADR-009) ; hors de l'espace « vs-… » des
     * identifiants de clients générés, il ne désigne aucune application.
     */
    public static final String CLIENT_PORTAIL = "portail";

    public ContexteAcces {
        organisation = organisation == null ? Optional.empty() : organisation;
        client = client == null ? Optional.empty() : client;
    }

    public static ContexteAcces anonyme() {
        return new ContexteAcces(Optional.empty(), Optional.empty());
    }

    /** Vrai pour un utilisateur du portail agissant sans application. */
    public boolean estPortail() {
        return client.filter(CLIENT_PORTAIL::equals).isPresent();
    }
}
