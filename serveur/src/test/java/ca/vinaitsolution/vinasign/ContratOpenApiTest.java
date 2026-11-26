package ca.vinaitsolution.vinasign;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * VS-090 — le contrat OpenAPI v1 publié est celui de référence, versionné avec le serveur ({@code contrat/openapi-v1.json}) :
 * tout changement de l'API se voit dans la revue. Pour accepter un changement voulu :
 * {@code mvn test -Dtest=ContratOpenApiTest -Dcontrat.ecrire=true}. Chaque opération de {@code /api/v1} déclare ses erreurs
 * RFC 9457.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDeTest.class)
class ContratOpenApiTest {

    static final Path REFERENCE = Path.of("contrat", "openapi-v1.json");
    private static final ObjectMapper TRIE = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired MockMvc mvc;

    private JsonNode publie() throws Exception {
        JsonNode brut = TRIE.readTree(mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        ((ObjectNode) brut).remove("servers");
        return brut;
    }

    /** Forme canonique : clés triées, indentation stable. */
    static String canonique(JsonNode n) throws Exception {
        return TRIE.writeValueAsString(TRIE.treeToValue(n, Object.class)) + "\n";
    }

    @Test
    void le_contrat_publie_est_le_contrat_de_reference() throws Exception {
        String publie = canonique(publie());
        if (Boolean.getBoolean("contrat.ecrire")) {
            Files.writeString(REFERENCE, publie, StandardCharsets.UTF_8);
        }
        assertTrue(Files.exists(REFERENCE), "Contrat de référence absent : " + REFERENCE.toAbsolutePath());
        String reference = Files.readString(REFERENCE, StandardCharsets.UTF_8);
        assertEquals(reference, publie, "L'API a changé sans que le contrat de référence soit mis à jour (-Dcontrat.ecrire=true), "
                + "ou le contrat a été modifié à la main.");
    }

    @Test
    void chaque_operation_v1_declare_ses_erreurs_rfc_9457_et_le_contrat_porte_sa_version() throws Exception {
        JsonNode api = publie();
        assertEquals("v1", api.at("/info/version").asText());
        assertEquals("bearer", api.at("/components/securitySchemes/jeton/scheme").asText());
        List<String> manquantes = new ArrayList<>();
        api.get("paths").fields().forEachRemaining(chemin -> {
            if (!chemin.getKey().startsWith("/api/v1/")) {
                return;
            }
            chemin.getValue().fields().forEachRemaining(op -> {
                for (String code : List.of("400", "401", "403", "404", "409", "422", "429")) {
                    if (!op.getValue().at("/responses/" + code + "/content").has("application/problem+json")) {
                        manquantes.add(op.getKey().toUpperCase() + " " + chemin.getKey() + " " + code);
                    }
                }
            });
        });
        assertEquals(List.of(), manquantes);
        List<String> identifiants = new ArrayList<>();
        api.get("paths").forEach(item -> item.forEach(op -> identifiants.add(op.path("operationId").asText())));
        assertEquals(identifiants.size(), new java.util.HashSet<>(identifiants).size(), "identifiants d'opération uniques");
        assertTrue(identifiants.stream().noneMatch(id -> id.matches(".*\\d+$")), "aucun suffixe numérique : " + identifiants);
        assertTrue(api.get("paths").size() > 30, "inventaire complet");
        assertEquals(5, api.at("/components/schemas/Probleme/properties").size(), "type, title, status, detail, instance");
    }
}
