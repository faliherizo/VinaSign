package ca.vinaitsolution.vinasign.acces.infrastructure.web;

import ca.vinaitsolution.vinasign.acces.application.GererUtilisateurs;
import ca.vinaitsolution.vinasign.acces.application.dto.VueUtilisateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Set;
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

/** Utilisateurs du portail de l'organisation courante (VS-014) : réservé à son administrateur. */
@RestController
@RequestMapping("/api/v1/utilisateurs")
@PreAuthorize("hasRole('administrateur-organisation')")
public class UtilisateurController {

    /** Invitation : le courriel sert à lier l'identité du fournisseur à la première connexion. */
    public record Invitation(@NotBlank @Size(max = 320) String courriel, @NotEmpty @Size(max = 10) Set<String> roles) {
    }

    /** Rôles attribués (codes du référentiel role-portail). */
    public record Roles(@NotEmpty @Size(max = 10) Set<String> roles) {
    }

    private final GererUtilisateurs gestion;

    public UtilisateurController(GererUtilisateurs gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<VueUtilisateur> inviter(@Valid @RequestBody Invitation corps) {
        VueUtilisateur u = gestion.inviter(corps.courriel(), corps.roles());
        return ResponseEntity.created(URI.create("/api/v1/utilisateurs/" + u.id())).body(u);
    }

    @GetMapping
    public List<VueUtilisateur> lister() {
        return gestion.lister();
    }

    @PutMapping("/{id}/roles")
    @Transactional
    public VueUtilisateur modifierRoles(@PathVariable UUID id, @Valid @RequestBody Roles corps) {
        return gestion.modifierRoles(id, corps.roles());
    }

    @PostMapping("/{id}/desactivation")
    @Transactional
    public VueUtilisateur desactiver(@PathVariable UUID id) {
        return gestion.desactiver(id);
    }
}
