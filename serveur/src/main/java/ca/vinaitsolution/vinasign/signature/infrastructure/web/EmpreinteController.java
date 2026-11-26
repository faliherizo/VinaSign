package ca.vinaitsolution.vinasign.signature.infrastructure.web;

import ca.vinaitsolution.vinasign.signature.application.SignerEmpreinte;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Base64;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Signature d'une empreinte par le cachet de la plateforme (VS-034) ; le document ne transite jamais. */
@RestController
public class EmpreinteController {

    /** Empreinte SHA-256 du document, en hexadécimal. */
    public record Demande(@NotBlank @Size(min = 64, max = 64) String sha256) {
    }

    /** CMS détaché (DER encodé en base64) ; {@code horodatee} : jeton RFC 3161 inclus. */
    public record Signature(String cms, boolean horodatee) {
    }

    private final SignerEmpreinte signature;

    public EmpreinteController(SignerEmpreinte signature) {
        this.signature = signature;
    }

    @PostMapping("/api/v1/empreintes/signature")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_empreintes-signer')")
    public Signature signer(@Valid @RequestBody Demande corps) {
        SignerEmpreinte.Resultat r = signature.executer(corps.sha256());
        return new Signature(Base64.getEncoder().encodeToString(r.cms()), r.horodate());
    }
}
