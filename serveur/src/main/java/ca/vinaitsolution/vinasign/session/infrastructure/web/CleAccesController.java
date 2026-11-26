package ca.vinaitsolution.vinasign.session.infrastructure.web;

import ca.vinaitsolution.vinasign.session.application.CleAccesSignataire;
import ca.vinaitsolution.vinasign.session.application.port.VerificationWebAuthn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Base64;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Clés d'accès du signataire authentifié par sa session (VS-043) ; valeurs binaires en base64url, comme WebAuthn les rend. */
@RestController
@RequestMapping("/api/v1/signataire/cles-acces")
public class CleAccesController {

    public record Enregistrement(@NotBlank @Size(max = 20_000) String attestationObject, @NotBlank @Size(max = 4_000) String clientDataJSON) {
    }

    public record Assertion(@NotBlank @Size(max = 2_000) String credentialId, @NotBlank @Size(max = 4_000) String authenticatorData,
                            @NotBlank @Size(max = 4_000) String clientDataJSON, @NotBlank @Size(max = 2_000) String signature) {
    }

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    private final CleAccesSignataire cles;

    public CleAccesController(CleAccesSignataire cles) {
        this.cles = cles;
    }

    @PostMapping("/enregistrement/options")
    @Transactional
    public CleAccesSignataire.Options optionsEnregistrement() {
        return cles.optionsEnregistrement();
    }

    @PostMapping("/enregistrement")
    @Transactional
    public ResponseEntity<Void> enregistrer(@Valid @RequestBody Enregistrement corps) {
        cles.enregistrer(decoder(corps.attestationObject()), decoder(corps.clientDataJSON()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/authentification/options")
    @Transactional
    public CleAccesSignataire.Options optionsAuthentification() {
        return cles.optionsAuthentification();
    }

    @PostMapping("/authentification")
    @Transactional
    public ResponseEntity<Void> authentifier(@Valid @RequestBody Assertion corps) {
        cles.authentifier(new VerificationWebAuthn.Assertion(decoder(corps.credentialId()), decoder(corps.authenticatorData()),
                decoder(corps.clientDataJSON()), decoder(corps.signature())));
        return ResponseEntity.noContent().build();
    }

    private static byte[] decoder(String b64url) {
        return B64.decode(b64url);
    }
}
