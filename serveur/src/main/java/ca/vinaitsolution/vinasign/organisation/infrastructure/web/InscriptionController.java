package ca.vinaitsolution.vinasign.organisation.infrastructure.web;

import ca.vinaitsolution.vinasign.organisation.application.InscrireOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.dto.VueInscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscription en libre-service (VS-122). La demande et les étapes du demandeur sont publiques, authentifiées par le jeton
 * du lien reçu par courriel (dans le corps, jamais dans l'URL) ; la validation appartient à l'administrateur de la plateforme.
 * Pas de transaction ici : chaque étape s'exécute au nom de la future organisation.
 */
@RestController
public class InscriptionController {

    public record Demande(@NotBlank @Size(max = 200) String organisation, @NotBlank @Size(max = 320) String courriel,
                          @Size(max = 253) String domaine, @NotBlank @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?") String langue) {
    }

    public record Jeton(@NotBlank @Size(max = 100) String jeton) {
    }

    public record Refus(@Size(max = 500) String motif) {
    }

    private final InscrireOrganisation inscription;

    public InscriptionController(InscrireOrganisation inscription) {
        this.inscription = inscription;
    }

    @PostMapping("/api/v1/signup")
    public ResponseEntity<Void> demander(@Valid @RequestBody Demande d) {
        inscription.demander(d.organisation(), d.courriel(), d.domaine(), d.langue());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/signup/confirmation")
    public VueInscription confirmer(@Valid @RequestBody Jeton j) {
        return inscription.confirmer(j.jeton());
    }

    @PostMapping("/api/v1/signup/consultation")
    public VueInscription consulter(@Valid @RequestBody Jeton j) {
        return inscription.consulter(j.jeton());
    }

    @PostMapping("/api/v1/signup/domain-verification")
    public VueInscription prouverDomaine(@Valid @RequestBody Jeton j) {
        return inscription.prouverDomaine(j.jeton());
    }

    @GetMapping("/api/v1/platform/signups")
    @PreAuthorize("hasRole('administrateur-plateforme')")
    public List<VueInscription> aValider() {
        return inscription.aValider();
    }

    @PostMapping("/api/v1/platform/signups/{id}/approval")
    @PreAuthorize("hasRole('administrateur-plateforme')")
    public VueInscription valider(@PathVariable UUID id) {
        return inscription.valider(id);
    }

    @PostMapping("/api/v1/platform/signups/{id}/rejection")
    @PreAuthorize("hasRole('administrateur-plateforme')")
    public VueInscription refuser(@PathVariable UUID id, @Valid @RequestBody Refus r) {
        return inscription.refuser(id, r.motif());
    }
}
