package ca.vinaitsolution.vinasign.organisation.infrastructure.web;

import ca.vinaitsolution.vinasign.organisation.application.GererMarque;
import ca.vinaitsolution.vinasign.organisation.domain.Marque;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Base64;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marque de l'organisation (VS-123) : nom affiché, couleur (contraste calculé), logo PNG en base64, sous-domaine. Définie par
 * l'administrateur de l'organisation au portail, ou par une application qui en a reçu la portée.
 */
@RestController
@RequestMapping("/api/v1/branding")
@PreAuthorize("hasAuthority('SCOPE_marque-gerer') or hasRole('administrateur-organisation')")
public class MarqueController {

    public record VueMarque(String nomAffiche, String couleurPrimaire, String logo, String sousDomaine) {

        static VueMarque de(Marque m) {
            return new VueMarque(m.nomAffiche(), m.couleurPrimaire(), m.logo() == null ? null : Base64.getEncoder().encodeToString(m.logo()), m.sousDomaine());
        }
    }

    public record Demande(@Size(max = 80) String nomAffiche, @Size(max = 7) String couleurPrimaire, @Size(max = 140_000) String logo,
                          @Size(max = 40) String sousDomaine) {
    }

    private final GererMarque marque;

    public MarqueController(GererMarque marque) {
        this.marque = marque;
    }

    @PutMapping
    @Transactional
    public VueMarque definir(@Valid @RequestBody Demande d) {
        byte[] logo;
        try {
            logo = d.logo() == null || d.logo().isBlank() ? null : Base64.getDecoder().decode(d.logo());
        } catch (IllegalArgumentException e) {
            throw new Marque.MarqueInvalide("Logo illisible : PNG en base64 attendu.");
        }
        return VueMarque.de(marque.definir(new Marque(d.nomAffiche(), d.couleurPrimaire(), logo, d.sousDomaine())));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<VueMarque> lire() {
        return marque.lire().map(VueMarque::de).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
