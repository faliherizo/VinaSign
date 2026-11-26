package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class ContratApiConfigurationTest {

    @Test
    void seules_les_operations_de_l_api_v1_recoivent_les_erreurs_rfc_9457() {
        OpenAPI api = new OpenAPI().components(new Components()).paths(new Paths()
                .addPathItem("/api/v1/enveloppes", new PathItem().get(new Operation().responses(new ApiResponses())))
                .addPathItem("/actuator/health", new PathItem().get(new Operation().responses(new ApiResponses()))));
        new ContratApiConfiguration().erreursDeclarees().customise(api);
        assertEquals(7, api.getPaths().get("/api/v1/enveloppes").getGet().getResponses().size());
        assertTrue(api.getPaths().get("/actuator/health").getGet().getResponses().isEmpty(), "hors du contrat v1 : inchangé");
        assertNotNull(api.getComponents().getSchemas().get("Probleme"));
    }
}
