package ca.vinaitsolution.vinasign.acces.infrastructure.web;

import ca.vinaitsolution.vinasign.acces.application.GererUtilisateurs;
import ca.vinaitsolution.vinasign.acces.application.dto.VueUtilisateur;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Premier administrateur d'une organisation, invité par l'administration de la plateforme (VS-014). */
@RestController
@PreAuthorize("hasRole('administrateur-plateforme')")
public class AdministrateurOrganisationController {

    /** Courriel du futur administrateur. */
    public record Invitation(@NotBlank @Size(max = 320) String courriel) {
    }

    private final GererUtilisateurs gestion;

    public AdministrateurOrganisationController(GererUtilisateurs gestion) {
        this.gestion = gestion;
    }

    @PostMapping("/api/v1/organisations/{id}/administrateurs")
    @Transactional
    public ResponseEntity<VueUtilisateur> inviter(@PathVariable UUID id, @Valid @RequestBody Invitation corps) {
        VueUtilisateur u = gestion.inviterAdministrateur(new IdentifiantOrganisation(id), corps.courriel());
        return ResponseEntity.created(URI.create("/api/v1/utilisateurs/" + u.id())).body(u);
    }
}
