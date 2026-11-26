package ca.vinaitsolution.vinasign.organisation.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.AdministrerOrganisations;
import ca.vinaitsolution.vinasign.organisation.application.dto.VueOrganisation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration des organisations (VS-010) : réservée à l'administrateur de la plateforme. */
@RestController
@RequestMapping("/api/v1/organisations")
@PreAuthorize("hasRole('administrateur-plateforme')")
public class OrganisationController {

    /** Corps de création. */
    public record NouvelleOrganisation(@NotBlank @Size(max = 200) String nom) {
    }

    /** Plan souscrit : code du référentiel plan. */
    public record NouveauPlan(@NotBlank @Size(max = 60) String plan) {
    }

    private final AdministrerOrganisations administration;

    public OrganisationController(AdministrerOrganisations administration) {
        this.administration = administration;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<VueOrganisation> creer(@Valid @RequestBody NouvelleOrganisation corps) {
        VueOrganisation creee = administration.creer(corps.nom());
        return ResponseEntity.created(URI.create("/api/v1/organisations/" + creee.id())).body(creee);
    }

    @GetMapping("/{id}")
    public VueOrganisation consulter(@PathVariable UUID id) {
        return administration.consulter(new IdentifiantOrganisation(id));
    }

    @PostMapping("/{id}/suspension")
    @Transactional
    public VueOrganisation suspendre(@PathVariable UUID id) {
        return administration.suspendre(new IdentifiantOrganisation(id));
    }

    @PostMapping("/{id}/reactivation")
    @Transactional
    public VueOrganisation reactiver(@PathVariable UUID id) {
        return administration.reactiver(new IdentifiantOrganisation(id));
    }

    @PutMapping("/{id}/plan")
    @Transactional
    public VueOrganisation changerPlan(@PathVariable UUID id, @Valid @RequestBody NouveauPlan corps) {
        return administration.changerPlan(new IdentifiantOrganisation(id), corps.plan());
    }
}
