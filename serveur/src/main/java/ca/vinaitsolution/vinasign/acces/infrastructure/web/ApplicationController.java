package ca.vinaitsolution.vinasign.acces.infrastructure.web;

import ca.vinaitsolution.vinasign.acces.application.EnregistrerApplication;
import ca.vinaitsolution.vinasign.acces.application.GererApplication;
import ca.vinaitsolution.vinasign.acces.application.dto.VueApplication;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Set;
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

/** Applications clientes de l'organisation courante (VS-011, VS-013) : réservé à l'administrateur de l'organisation. */
@RestController
@RequestMapping("/api/v1/applications")
@PreAuthorize("hasRole('administrateur-organisation')")
public class ApplicationController {

    /** Corps d'enregistrement : {@code jwks} est la JWKS publique de l'application, en JSON. */
    public record NouvelleApplication(@NotBlank @Size(max = 120) String nom, @NotBlank String environnement,
                                      @NotBlank @Size(max = 20_000) String jwks, @NotNull @Size(max = 50) Set<String> portees,
                                      @Size(max = 20) Set<String> origines) {
    }

    /** Nouveau jeu de clés publiques : l'ancienne et la nouvelle clé pendant la rotation, puis la nouvelle seule. */
    public record NouvellesCles(@NotBlank @Size(max = 20_000) String jwks) {
    }

    private final EnregistrerApplication enregistrement;
    private final GererApplication gestion;

    public ApplicationController(EnregistrerApplication enregistrement, GererApplication gestion) {
        this.enregistrement = enregistrement;
        this.gestion = gestion;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<VueApplication> enregistrer(@Valid @RequestBody NouvelleApplication corps) {
        VueApplication vue = enregistrement.executer(new EnregistrerApplication.Demande(corps.nom(), corps.environnement(), corps.jwks(),
                corps.portees(), corps.origines()));
        return ResponseEntity.created(URI.create("/api/v1/applications/" + vue.clientId())).body(vue);
    }

    @GetMapping
    public List<VueApplication> lister() {
        return enregistrement.lister();
    }

    @PostMapping("/{clientId}/revocation")
    @Transactional
    public VueApplication revoquer(@PathVariable String clientId) {
        return gestion.revoquer(new IdentifiantClient(clientId));
    }

    @PutMapping("/{clientId}/cles")
    @Transactional
    public VueApplication renouvelerCles(@PathVariable String clientId, @Valid @RequestBody NouvellesCles corps) {
        return gestion.renouvelerCles(new IdentifiantClient(clientId), corps.jwks());
    }
}
