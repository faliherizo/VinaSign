package ca.vinaitsolution.vinasign.signature.infrastructure.web;

import ca.vinaitsolution.vinasign.signature.application.VerifierDocument;
import ca.vinaitsolution.vinasign.signature.application.dto.RapportVerification;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vérification publique (VS-036) : un PDF signé (corps application/pdf), ou une empreinte et sa signature CMS (JSON).
 * Publique et limitée en débit par adresse ; le contenu soumis n'est jamais conservé ni journalisé.
 */
@RestController
public class VerificationController {

    /** Empreinte SHA-256 (hexadécimal) et CMS détaché (base64). */
    public record Empreinte(@NotBlank @Size(min = 64, max = 64) String sha256, @NotBlank @Size(max = 200_000) String cms) {
    }

    private final VerifierDocument verification;
    private final long tailleMax;

    public VerificationController(VerifierDocument verification) {
        this.verification = verification;
        this.tailleMax = verification.tailleMax();
    }

    @PostMapping(value = "/api/v1/verifications", consumes = "application/pdf")
    public RapportVerification pdf(HttpServletRequest requete) throws IOException {
        try (InputStream corps = requete.getInputStream()) {
            return verification.pdf(corps.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, tailleMax + 1)));
        }
    }

    @PostMapping(value = "/api/v1/verifications", consumes = "application/json")
    public RapportVerification empreinte(@Valid @RequestBody Empreinte corps) {
        return verification.empreinte(corps.sha256(), corps.cms());
    }
}
