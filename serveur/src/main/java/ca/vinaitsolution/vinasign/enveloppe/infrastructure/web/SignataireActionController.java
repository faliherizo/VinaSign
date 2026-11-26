package ca.vinaitsolution.vinasign.enveloppe.infrastructure.web;

import ca.vinaitsolution.vinasign.enveloppe.application.AgirCommeSignataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Actions du signataire authentifié par sa session (VS-050 à VS-052, VS-045). */
@RestController
@RequestMapping("/api/v1/signataire")
public class SignataireActionController {

    /** Image PNG en base64 (dessin) ou nom tapé ; valeurs des champs par identifiant. */
    public record Signature(@Size(max = 420_000) String image, @Size(max = 200) String nomSaisi, Map<UUID, @Size(max = 2000) String> valeurs) {
    }

    /** Motif facultatif (code du référentiel motif-refus). */
    public record Refus(@Size(max = 60) String motif) {
    }

    private final AgirCommeSignataire signataire;

    public SignataireActionController(AgirCommeSignataire signataire) {
        this.signataire = signataire;
    }

    @GetMapping(value = "/document", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> document() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(signataire.document());
    }

    @GetMapping("/champs")
    @Transactional(readOnly = true)
    public List<Champ> champs() {
        return signataire.mesChamps();
    }

    @PostMapping("/signature")
    @Transactional
    public ResponseEntity<Void> signer(@Valid @RequestBody Signature corps) {
        byte[] image;
        try {
            image = corps.image() == null || corps.image().isBlank() ? null : Base64.getDecoder().decode(corps.image().replaceFirst("^data:image/png;base64,", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Image de signature illisible : PNG en base64 attendu.", e);
        }
        signataire.signer(new AgirCommeSignataire.Signature(image, corps.nomSaisi(), corps.valeurs()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refus")
    @Transactional
    public ResponseEntity<Void> refuser(@Valid @RequestBody Refus corps) {
        signataire.refuser(corps.motif());
        return ResponseEntity.noContent().build();
    }
}
