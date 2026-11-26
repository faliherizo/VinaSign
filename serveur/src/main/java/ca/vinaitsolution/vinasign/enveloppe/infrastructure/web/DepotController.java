package ca.vinaitsolution.vinasign.enveloppe.infrastructure.web;

import ca.vinaitsolution.vinasign.enveloppe.application.DeposerDocument;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.EnveloppeProprietes;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * URL présignée de dépôt (VS-021) : publique, authentifiée par son jeton de 256 bits seulement (motivée dans
 * securite/donnees/surface-publique.json) ; un seul objet, avant l'échéance. Le corps est lu dans la limite de la taille
 * permise (plus un octet, pour la détecter), jamais en entier. Le document n'est retenu qu'à la confirmation.
 */
@RestController
public class DepotController {

    private final DeposerDocument depot;
    private final long tailleMax;

    public DepotController(DeposerDocument depot, EnveloppeProprietes proprietes) {
        this.depot = depot;
        this.tailleMax = proprietes.tailleMax();
    }

    @PutMapping("/api/v1/depots/{jeton}")
    @Transactional
    public ResponseEntity<Void> deposer(@PathVariable String jeton, HttpServletRequest requete) throws IOException {
        try (InputStream corps = requete.getInputStream()) {
            depot.recevoir(jeton, corps.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, tailleMax + 1)));
        }
        return ResponseEntity.noContent().build();
    }
}
