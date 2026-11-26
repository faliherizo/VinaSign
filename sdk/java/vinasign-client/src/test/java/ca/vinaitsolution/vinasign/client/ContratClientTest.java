package ca.vinaitsolution.vinasign.client;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Test de contrat du SDK (VS-090, VS-092) : chaque opération du contrat OpenAPI v1 a sa méthode dans le client généré. */
class ContratClientTest {

    @Test
    void chaque_operation_du_contrat_est_appelable_depuis_le_sdk() throws Exception {
        JsonNode contrat = new ObjectMapper().readTree(new File("../../../serveur/contrat/openapi-v1.json"));
        List<String> operations = new ArrayList<>();
        contrat.get("paths").forEach(item -> item.forEach(op -> operations.add(op.get("operationId").asText())));
        Set<String> methodes = Arrays.stream(new File("target/classes/ca/vinaitsolution/vinasign/client/genere/api").list())
                .filter(f -> f.endsWith("Api.class")).flatMap(f -> {
                    try {
                        return Arrays.stream(Class.forName("ca.vinaitsolution.vinasign.client.genere.api." + f.replace(".class", "")).getMethods());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                }).map(Method::getName).collect(Collectors.toSet());
        assertTrue(operations.size() > 40);
        assertEquals(List.of(), operations.stream().filter(o -> !methodes.contains(o)).toList(), "opérations sans méthode dans le SDK");
    }
}
