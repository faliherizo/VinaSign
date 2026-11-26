package ca.vinaitsolution.vinasign.integration.infrastructure.web;

import ca.vinaitsolution.vinasign.integration.application.GererWebhooks;
import ca.vinaitsolution.vinasign.integration.domain.DestinationWebhook;
import ca.vinaitsolution.vinasign.integration.domain.Livraison;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Destinations des webhooks de l'application (VS-091) ; le secret n'est rendu qu'à la création. */
@RestController
@RequestMapping("/api/v1/webhooks")
@PreAuthorize("hasAuthority('SCOPE_webhooks-gerer')")
public class WebhookController {

    /** URL HTTPS de réception ; noms d'événements (vide : tous). */
    public record Demande(@NotBlank @Size(max = 2000) String url, @Size(max = 30) Set<@Size(max = 80) String> evenements) {
    }

    public record VueDestination(UUID id, String url, Set<String> evenements, Instant creeLe) {

        static VueDestination de(DestinationWebhook d) {
            return new VueDestination(d.id(), d.url(), d.evenements(), d.creeLe());
        }
    }

    /** Secret de signature (HMAC-SHA256) : à conserver par l'application, jamais rendu de nouveau. */
    public record VueCreee(UUID id, String url, Set<String> evenements, Instant creeLe, String secret) {
    }

    public record VueLivraison(UUID id, UUID evenement, int tentatives, Integer dernierCode, Instant prochaineTentative, Instant livreLe, Instant abandonneLe) {

        static VueLivraison de(Livraison l) {
            return new VueLivraison(l.id(), l.evenement(), l.tentatives(), l.dernierCode(), l.terminee() ? null : l.prochaineTentative(), l.livreLe(),
                    l.abandonneLe());
        }
    }

    private final GererWebhooks webhooks;

    public WebhookController(GererWebhooks webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<VueCreee> creer(@Valid @RequestBody Demande corps) {
        GererWebhooks.Creee c = webhooks.enregistrer(corps.url(), corps.evenements());
        DestinationWebhook d = c.destination();
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(new VueCreee(d.id(), d.url(), d.evenements(), d.creeLe(), c.secret()));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<VueDestination> lister() {
        return webhooks.lister().stream().map(VueDestination::de).toList();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> retirer(@PathVariable UUID id) {
        webhooks.retirer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/deliveries")
    @Transactional(readOnly = true)
    public List<VueLivraison> livraisons(@PathVariable UUID id) {
        return webhooks.livraisons(id).stream().map(VueLivraison::de).toList();
    }
}
