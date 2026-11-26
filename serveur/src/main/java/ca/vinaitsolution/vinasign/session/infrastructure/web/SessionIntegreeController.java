package ca.vinaitsolution.vinasign.session.infrastructure.web;

import ca.vinaitsolution.vinasign.session.application.OuvrirSessionIntegree;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Session intégrée d'un signataire pour l'application cliente (VS-046) : à intégrer en iframe, 5 minutes, usage unique. */
@RestController
public class SessionIntegreeController {

    /**
     * Signataire et actions permises (codes du référentiel action-signataire). Pour un utilisateur de l'application,
     * l'attestation signée par elle (VS-044) et, au besoin, le niveau d'authentification demandé (référentiel
     * niveau-authentification).
     */
    public record Demande(@NotNull UUID signataire, @NotEmpty @Size(max = 10) Set<String> actions, @Size(max = 8192) String attestation,
                          @Size(max = 30) String niveau) {
    }

    private final OuvrirSessionIntegree session;

    public SessionIntegreeController(OuvrirSessionIntegree session) {
        this.session = session;
    }

    @PostMapping("/api/v1/envelopes/{id}/sessions")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_sessions-creer')")
    public ResponseEntity<OuvrirSessionIntegree.Integration> creer(@PathVariable UUID id, @Valid @RequestBody Demande corps) {
        return ResponseEntity.status(201).body(session.executer(id, corps.signataire(), corps.actions(),
                new OuvrirSessionIntegree.Attestee(corps.attestation(), corps.niveau())));
    }
}
