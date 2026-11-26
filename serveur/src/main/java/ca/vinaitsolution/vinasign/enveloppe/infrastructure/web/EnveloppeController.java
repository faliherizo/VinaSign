package ca.vinaitsolution.vinasign.enveloppe.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.infrastructure.web.Idempotence;
import ca.vinaitsolution.vinasign.enveloppe.application.CreerEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.DefinirSignataires;
import ca.vinaitsolution.vinasign.enveloppe.application.DeposerDocument;
import ca.vinaitsolution.vinasign.enveloppe.application.PiloterEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.dto.VueDepot;
import ca.vinaitsolution.vinasign.enveloppe.application.dto.VueEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Enveloppes d'une application cliente (VS-020 à VS-025). Les statuts changent par des actions, jamais par une saisie. */
@RestController
@RequestMapping("/api/v1/envelopes")
public class EnveloppeController {

    /** Création : {@code mode} est un code du référentiel mode-enveloppe. */
    public record NouvelleEnveloppe(@NotBlank @Size(max = 200) String externalReference, @NotBlank @Size(max = 30) String mode,
                                    @NotNull Instant expiresAt) {
    }

    /** Signataire : {@code langue} (référentiel langue), {@code type} (référentiel type-signataire). */
    public record NouveauSignataire(@NotBlank @Size(max = 200) String nom, @Size(max = 320) String courriel,
                                    @NotBlank @Size(max = 30) String langue, @NotBlank @Size(max = 60) String type,
                                    @Min(1) @Max(100) int ordre, @Size(max = 300) String sujetExterne, @Size(max = 25) String telephone,
                                    @Size(max = 30) String niveau) {
    }

    /** Champ : type (référentiel type-champ), page, rectangle normalisé (0 à 1). */
    public record NouveauChamp(@NotNull UUID signataire, @NotBlank @Size(max = 30) String type, @NotBlank @Size(max = 120) String libelle,
                               @Min(1) int page, double x, double y, double largeur, double hauteur, boolean obligatoire) {
    }

    private final CreerEnveloppe creation;
    private final ca.vinaitsolution.vinasign.enveloppe.application.DefinirChamps champs;
    private final DefinirSignataires signataires;
    private final PiloterEnveloppe pilotage;
    private final DeposerDocument depot;
    private final Idempotence idempotence;

    public EnveloppeController(CreerEnveloppe creation, DefinirSignataires signataires, PiloterEnveloppe pilotage, DeposerDocument depot,
                               Idempotence idempotence, ca.vinaitsolution.vinasign.enveloppe.application.DefinirChamps champs) {
        this.creation = creation;
        this.champs = champs;
        this.signataires = signataires;
        this.pilotage = pilotage;
        this.depot = depot;
        this.idempotence = idempotence;
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_enveloppes-creer') or hasRole('preparateur')")
    public ResponseEntity<?> creer(@RequestHeader(value = Idempotence.ENTETE, required = false) String cle,
                                   @Valid @RequestBody NouvelleEnveloppe corps) {
        return idempotence.executer(cle, corps, () -> {
            VueEnveloppe e = creation.executer(new CreerEnveloppe.Demande(corps.externalReference(), corps.mode(), corps.expiresAt()));
            return ResponseEntity.created(URI.create("/api/v1/envelopes/" + e.id())).body(e);
        });
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_enveloppes-lire') or hasAnyRole('preparateur', 'auditeur')")
    public VueEnveloppe consulter(@PathVariable UUID id) {
        return pilotage.consulter(new IdentifiantEnveloppe(id));
    }

    @PutMapping("/{id}/signers")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_signataires-gerer') or hasRole('preparateur')")
    public VueEnveloppe definirSignataires(@PathVariable UUID id, @Valid @RequestBody @NotNull @Size(min = 1, max = 50) List<@Valid NouveauSignataire> corps) {
        return signataires.executer(new IdentifiantEnveloppe(id), corps.stream()
                .map(s -> new DefinirSignataires.Demande(s.nom(), s.courriel(), s.langue(), s.type(), s.ordre(), s.sujetExterne(), s.telephone(), s.niveau()))
                .toList());
    }

    @GetMapping("/{id}/fields")
    @PreAuthorize("hasAuthority('SCOPE_enveloppes-lire') or hasAnyRole('preparateur', 'auditeur')")
    public List<ca.vinaitsolution.vinasign.enveloppe.domain.Champ> champs(@PathVariable UUID id) {
        return champs.lire(new IdentifiantEnveloppe(id));
    }

    @PutMapping("/{id}/fields")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_enveloppes-creer') or hasRole('preparateur')")
    public List<ca.vinaitsolution.vinasign.enveloppe.domain.Champ> definirChamps(@PathVariable UUID id,
                                                                           @Valid @RequestBody @Size(max = 500) List<@Valid NouveauChamp> corps) {
        return champs.executer(new IdentifiantEnveloppe(id), corps.stream().map(c -> new ca.vinaitsolution.vinasign.enveloppe.application.DefinirChamps.Demande(
                c.signataire(), c.type(), c.libelle(), c.page(), c.x(), c.y(), c.largeur(), c.hauteur(), c.obligatoire())).toList());
    }

    @PostMapping("/{id}/documents")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_documents-deposer') or hasRole('preparateur')")
    public ResponseEntity<VueDepot> demanderDepot(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(depot.demander(new IdentifiantEnveloppe(id)));
    }

    @PostMapping("/{id}/documents/{depotId}/confirmation")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_documents-deposer') or hasRole('preparateur')")
    public VueEnveloppe confirmerDepot(@PathVariable UUID id, @PathVariable UUID depotId) {
        return depot.confirmer(new IdentifiantEnveloppe(id), depotId);
    }

    @PostMapping("/{id}/send")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_enveloppes-envoyer') or hasRole('preparateur')")
    public VueEnveloppe envoyer(@PathVariable UUID id) {
        return pilotage.envoyer(new IdentifiantEnveloppe(id));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_enveloppes-envoyer') or hasRole('preparateur')")
    public VueEnveloppe annuler(@PathVariable UUID id) {
        return pilotage.annuler(new IdentifiantEnveloppe(id));
    }
}
