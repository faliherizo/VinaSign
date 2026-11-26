package ca.vinaitsolution.vinasign.enveloppe.infrastructure.web;

import ca.vinaitsolution.vinasign.enveloppe.application.PlacerParAncres;
import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Champs placés par ancres textuelles du document (VS-063) ; remplace les champs de l'enveloppe, comme PUT /fields. */
@RestController
public class AncreController {

    /**
     * @param ancre    texte repère sans blanc, tel qu'écrit dans le document (ex. {@code {{signature:1}}})
     * @param ordre    ordre du signataire à qui le champ est assigné
     * @param largeur  largeur du champ, en fraction de la largeur de page
     */
    public record Ancre(@NotBlank @Pattern(regexp = "\\S{3,60}") String ancre, @Min(1) int ordre, @NotBlank @Size(max = 30) String type,
                        @NotBlank @Size(max = 120) String libelle, @DecimalMin("0.01") @DecimalMax("1") double largeur,
                        @DecimalMin("0.01") @DecimalMax("1") double hauteur, boolean obligatoire) {
    }

    private final PlacerParAncres placement;

    public AncreController(PlacerParAncres placement) {
        this.placement = placement;
    }

    @PutMapping("/api/v1/envelopes/{id}/fields/anchors")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_enveloppes-creer') or hasRole('preparateur')")
    public List<Champ> placer(@PathVariable UUID id, @Valid @RequestBody @Size(min = 1, max = 100) List<@Valid Ancre> corps) {
        return placement.executer(new IdentifiantEnveloppe(id), corps.stream()
                .map(a -> new PlacerParAncres.Demande(a.ancre(), a.ordre(), a.type(), a.libelle(), a.largeur(), a.hauteur(), a.obligatoire())).toList());
    }
}
