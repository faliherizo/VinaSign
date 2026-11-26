package ca.vinaitsolution.vinasign.session.infrastructure.web;

import ca.vinaitsolution.vinasign.session.application.VerifierIdentite;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentite;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vérification d'identité (VS-131) : démarrée et suivie par le signataire authentifié par sa session ; conclue par le rappel
 * du fournisseur, public mais authentifié par sa signature.
 */
@RestController
public class VerificationIdentiteController {

    /** Suivi : état ({@code en-cours}, {@code reussie}, {@code echouee}) et méthode déclarée. */
    public record Suivi(UUID id, String etat, String methode) {

        static Suivi de(VerificationIdentite v) {
            return new Suivi(v.id(), v.etat().code(), v.methode());
        }
    }

    private final VerifierIdentite verification;

    public VerificationIdentiteController(VerifierIdentite verification) {
        this.verification = verification;
    }

    @PostMapping("/api/v1/signataire/verification-identite")
    @Transactional
    public VerifierIdentite.Demarrage demarrer() {
        return verification.demarrer();
    }

    @GetMapping("/api/v1/signataire/verification-identite/{id}")
    @Transactional(readOnly = true)
    public Suivi consulter(@PathVariable UUID id) {
        return Suivi.de(verification.consulter(id));
    }

    @PostMapping(value = "/api/v1/verification-identite/rappel", consumes = "application/json")
    public ResponseEntity<Void> rappel(@RequestBody byte[] corps,
                                       @RequestHeader(value = "X-VinaSign-Signature", required = false) String signature) {
        verification.conclure(corps, signature);
        return ResponseEntity.noContent().build();
    }
}
