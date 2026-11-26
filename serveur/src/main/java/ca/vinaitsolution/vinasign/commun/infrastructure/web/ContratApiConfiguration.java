package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrat OpenAPI v1 publié (VS-090) : métadonnées, schéma d'authentification, et réponses d'erreur RFC 9457
 * ({@code application/problem+json}) déclarées sur chaque opération de {@code /api/v1}. Le contrat de référence est
 * versionné avec le serveur et comparé à chaque construction (ContratOpenApiTest).
 */
@Configuration
public class ContratApiConfiguration {

    static final String PROBLEME = "Probleme";
    static final Map<String, String> ERREURS = Map.of("400", "Requête mal formée", "401", "Authentification requise",
            "403", "Accès refusé", "404", "Introuvable", "409", "Conflit d'état", "422", "Données invalides", "429", "Débit dépassé");

    @Bean
    OpenAPI contratVinaSign() {
        return new OpenAPI().info(new Info().title("VinaSign API").version("v1")
                        .description("Plateforme de signature électronique de Vina IT Solution. Listes métier : référentiels (GET /api/v1/referentiels)."))
                .components(new Components().addSecuritySchemes("jeton", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT").description("Jeton du point /oauth2/token (client credentials, private_key_jwt).")));
    }

    /**
     * Identifiant d'opération stable et lisible pour les SDK : contrôleur sans suffixe, puis méthode
     * (ex. {@code referentielLister}), au lieu des suffixes numériques que la génération ajoute aux homonymes.
     */
    @Bean
    org.springdoc.core.customizers.OperationCustomizer identifiantsStables() {
        return (operation, methode) -> {
            String controleur = methode.getBeanType().getSimpleName().replaceAll("Controller$", "");
            String nom = methode.getMethod().getName();
            return operation.operationId(Character.toLowerCase(controleur.charAt(0)) + controleur.substring(1)
                    + Character.toUpperCase(nom.charAt(0)) + nom.substring(1));
        };
    }

    /** Schéma du problème ajouté après la génération des composants (springdoc les reconstruit à partir des contrôleurs). */
    @Bean
    OpenApiCustomizer erreursDeclarees() {
        return api -> {
            api.getComponents().addSchemas(PROBLEME, new ObjectSchema()
                    .description("Problème RFC 9457 ; « type » est stable : un client réagit au type, jamais au texte.")
                    .addProperty("type", new StringSchema().format("uri")).addProperty("title", new StringSchema())
                    .addProperty("status", new IntegerSchema()).addProperty("detail", new StringSchema()).addProperty("instance", new StringSchema().format("uri")));
            declarer(api);
        };
    }

    private static void declarer(OpenAPI api) {
        api.getPaths().forEach((chemin, item) -> {
            if (!chemin.startsWith("/api/v1/")) {
                return;
            }
            item.readOperations().forEach(op -> ERREURS.forEach((code, description) -> op.getResponses().putIfAbsent(code, new ApiResponse()
                    .description(description).content(new Content().addMediaType("application/problem+json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEME)))))));
        });
    }
}
