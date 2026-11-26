package ca.vinaitsolution.vinasign.integration.infrastructure.mcp;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Outil MCP en lecture seule (VS-094, porté de {@code OutilMcp} du CLM), exécuté pour l'application appelante. */
public interface OutilMcp {

    /** Nom stable de l'outil, tel que l'assistant l'appelle. */
    String nom();

    String titre();

    /** Description lue par l'assistant pour choisir l'outil. */
    String description();

    /** Schéma JSON des arguments. */
    Map<String, Object> schemaEntree();

    /** Résultat structuré ; {@link ArgumentInvalide} si refusé (l'assistant peut corriger). */
    Map<String, Object> executer(String application, Map<String, Object> arguments);

    /** Argument absent, mal formé ou désignant un objet inconnu de l'application. */
    class ArgumentInvalide extends IllegalArgumentException {

        public ArgumentInvalide(String message) {
            super(message);
        }
    }

    /** Identifiant UUID requis sous ce nom. */
    static UUID identifiant(Map<String, Object> arguments, String nom) {
        try {
            return UUID.fromString(String.valueOf(arguments.get(nom)));
        } catch (IllegalArgumentException e) {
            throw new ArgumentInvalide("Argument « " + nom + " » : identifiant (UUID) requis.");
        }
    }

    static Map<String, Object> schemaChaine(String nom, String description) {
        return Map.of("type", "object", "additionalProperties", false, "required", List.of(nom),
                "properties", Map.of(nom, Map.of("type", "string", "description", description)));
    }
}
