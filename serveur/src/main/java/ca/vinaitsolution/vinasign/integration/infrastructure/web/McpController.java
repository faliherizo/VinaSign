package ca.vinaitsolution.vinasign.integration.infrastructure.web;

import ca.vinaitsolution.vinasign.integration.infrastructure.mcp.ServeurMcp;

import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point MCP (VS-094), transport HTTP « streamable » sans flux : un message JSON-RPC par POST, une réponse JSON. Réservé à une
 * application cliente authentifiée (portée enveloppes-lire) ; l'assistant n'agit qu'en son nom et en lecture seule.
 */
@RestController
@RequestMapping("/api/v1/mcp")
@PreAuthorize("hasAuthority('SCOPE_enveloppes-lire')")
public class McpController {

    private final ServeurMcp serveur;
    private final ContexteAccesCourant acces;
    private final ObjectMapper json;

    public McpController(ServeurMcp serveur, ContexteAccesCourant acces, ObjectMapper json) {
        this.serveur = serveur;
        this.acces = acces;
        this.json = json;
    }

    /** Transaction en écriture : les outils ne modifient rien, mais chaque appel est consigné au journal d'audit. */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Object> recevoir(@RequestHeader(value = "MCP-Protocol-Version", required = false) String version,
                                           @RequestBody(required = false) String corps) {
        if (version != null && !ServeurMcp.VERSIONS.contains(version)) {
            return ResponseEntity.badRequest().body(Map.of("error", "version_non_prise_en_charge", "versions", ServeurMcp.VERSIONS));
        }
        JsonNode message;
        try {
            message = json.readTree(corps == null ? "" : corps);
        } catch (JsonProcessingException illisible) {
            return ResponseEntity.badRequest().body(ServeurMcp.erreur(null, -32700, "Corps JSON illisible."));
        }
        if (message != null && message.isArray()) {
            return ResponseEntity.badRequest().body(ServeurMcp.erreur(null, ServeurMcp.REQUETE_INVALIDE, "Les lots JSON-RPC ne sont pas pris en charge."));
        }
        ContexteAcces c = acces.courant();
        return serveur.traiter(c.organisation().orElseThrow(), c.client().orElseThrow(), message).<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }

    /** Aucun flux SSE n'est ouvert : la spécification prévoit alors 405. */
    @GetMapping
    public ResponseEntity<Void> flux() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }
}
