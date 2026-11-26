package ca.vinaitsolution.vinasign.etat.infrastructure.web;

import ca.vinaitsolution.vinasign.etat.application.GererIncidents;
import ca.vinaitsolution.vinasign.etat.domain.Incident;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publication des incidents (VS-124) par l'administrateur de la plateforme ; impact, composants et phase : codes de référentiel. */
@RestController
@RequestMapping("/api/v1/platform/incidents")
@PreAuthorize("hasRole('administrateur-plateforme')")
public class IncidentController {

    public record Communication(@NotBlank @Size(max = 30) String phase, @NotEmpty @Size(max = 10) Map<@Size(max = 10) String, @Size(max = 2000) String> message) {
    }

    public record NouvelIncident(@NotEmpty @Size(max = 10) Map<@Size(max = 10) String, @Size(max = 200) String> titre, @NotBlank @Size(max = 30) String impact,
                                 @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 40) String> composants, @NotNull @Valid Communication communication) {
    }

    /** Incident tel que publié. */
    public record VueIncident(UUID id, String impact, List<String> composants, Instant debut, Instant fin, int communications) {

        static VueIncident de(Incident i) {
            return new VueIncident(i.id(), i.impact(), i.composants(), i.debut(), i.fin(), i.misesAJour().size());
        }
    }

    private final GererIncidents incidents;

    public IncidentController(GererIncidents incidents) {
        this.incidents = incidents;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<VueIncident> ouvrir(@Valid @RequestBody NouvelIncident d) {
        Incident i = incidents.ouvrir(new GererIncidents.Ouverture(d.titre(), d.impact(), d.composants(),
                new GererIncidents.Communication(d.communication().phase(), d.communication().message())));
        return ResponseEntity.created(URI.create("/api/v1/platform/incidents/" + i.id())).body(VueIncident.de(i));
    }

    @PostMapping("/{id}/updates")
    @Transactional
    public VueIncident publier(@PathVariable UUID id, @Valid @RequestBody Communication c) {
        return VueIncident.de(incidents.publier(id, new GererIncidents.Communication(c.phase(), c.message())));
    }
}
