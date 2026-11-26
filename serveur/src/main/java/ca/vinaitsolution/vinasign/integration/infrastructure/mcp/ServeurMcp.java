package ca.vinaitsolution.vinasign.integration.infrastructure.mcp;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.JournalAudit;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.infrastructure.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Protocole MCP (Model Context Protocol) de VinaSign (VS-094, porté de {@code ServeurMcp} du CLM), JSON-RPC 2.0 :
 * {@code initialize}, {@code ping}, {@code tools/list}, {@code tools/call}. Outils en lecture seule, limités aux enveloppes de
 * l'application appelante. Une erreur d'outil devient un résultat {@code isError} que l'assistant peut lire et corriger ;
 * une erreur de protocole devient une erreur JSON-RPC. Chaque appel d'outil est consigné.
 */
public class ServeurMcp {

    /** Versions prises en charge, la plus récente en tête. */
    public static final List<String> VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26");

    public static final int REQUETE_INVALIDE = -32600;
    static final int METHODE_INCONNUE = -32601;
    static final int PARAMETRES_INVALIDES = -32602;

    /** Traçabilité des appels d'outils. */
    public record Trace(JournalAudit audit, ActeurCourant acteur, GenerateurIdentifiant identifiants, Clock horloge) {
    }

    private final Map<String, OutilMcp> outils;
    private final Trace trace;
    private final ObjectMapper json;

    public ServeurMcp(List<OutilMcp> outils, Trace trace, ObjectMapper json) {
        this.outils = outils.stream().collect(Collectors.toMap(OutilMcp::nom, Function.identity(), (a, b) -> {
            throw new IllegalStateException("Outil MCP en double : " + a.nom());
        }, LinkedHashMap::new));
        this.trace = trace;
        this.json = json;
    }

    /** Réponse JSON-RPC au message ; vide pour une notification, qui n'appelle pas de réponse. */
    public Optional<Map<String, Object>> traiter(IdentifiantOrganisation organisation, String application, JsonNode message) {
        if (message == null || !message.isObject() || !"2.0".equals(message.path("jsonrpc").asText())) {
            return Optional.of(erreur(null, REQUETE_INVALIDE, "Message JSON-RPC 2.0 attendu."));
        }
        JsonNode id = message.get("id");
        if (id == null || id.isNull()) {
            return Optional.empty();
        }
        JsonNode params = message.path("params");
        return Optional.of(switch (message.path("method").asText("")) {
            case "initialize" -> succes(id, initialiser(params));
            case "ping" -> succes(id, Map.of());
            case "tools/list" -> succes(id, Map.of("tools", outils.values().stream().map(ServeurMcp::description).toList()));
            case "tools/call" -> appeler(organisation, application, id, params);
            default -> erreur(id, METHODE_INCONNUE, "Méthode inconnue : " + message.path("method").asText(""));
        });
    }

    private static Map<String, Object> initialiser(JsonNode params) {
        String demandee = params.path("protocolVersion").asText("");
        return Map.of("protocolVersion", VERSIONS.contains(demandee) ? demandee : VERSIONS.get(0),
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of("name", "vinasign", "title", "VinaSign — signature électronique", "version", "1.0.0"),
                "instructions", "Outils en lecture seule sur les enveloppes de l'application appelante : statut, signataires, documents.");
    }

    private static Map<String, Object> description(OutilMcp o) {
        return Map.of("name", o.nom(), "title", o.titre(), "description", o.description(), "inputSchema", o.schemaEntree(),
                "annotations", Map.of("readOnlyHint", true, "destructiveHint", false, "idempotentHint", true, "openWorldHint", false));
    }

    private Map<String, Object> appeler(IdentifiantOrganisation organisation, String application, JsonNode id, JsonNode params) {
        OutilMcp outil = outils.get(params.path("name").asText(""));
        if (outil == null) {
            return erreur(id, PARAMETRES_INVALIDES, "Outil inconnu : " + params.path("name").asText(""));
        }
        JsonNode brut = params.path("arguments");
        if (!brut.isMissingNode() && !brut.isNull() && !brut.isObject()) {
            return erreur(id, PARAMETRES_INVALIDES, "Les arguments forment un objet JSON.");
        }
        Map<String, Object> arguments = brut.isObject() ? json.convertValue(brut, new TypeReference<Map<String, Object>>() { }) : Map.of();
        trace.audit().consigner(new EvenementAudit(trace.identifiants().suivant(), organisation, trace.acteur().courant(), "mcp-outil-appele",
                outil.nom(), trace.horloge().instant()));
        try {
            Map<String, Object> resultat = outil.executer(application, arguments);
            return succes(id, Map.of("content", List.of(Map.of("type", "text", "text", Json.ecrire(json, resultat, "Résultat d'outil"))),
                    "structuredContent", resultat, "isError", false));
        } catch (OutilMcp.ArgumentInvalide refus) {
            return succes(id, Map.of("content", List.of(Map.of("type", "text", "text", refus.getMessage())), "isError", true));
        }
    }

    private static Map<String, Object> succes(JsonNode id, Map<String, Object> resultat) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", resultat);
    }

    public static Map<String, Object> erreur(JsonNode id, int code, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("error", Map.of("code", code, "message", message));
        return r;
    }
}
