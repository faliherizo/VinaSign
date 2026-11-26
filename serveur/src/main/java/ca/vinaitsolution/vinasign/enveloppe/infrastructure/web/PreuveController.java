package ca.vinaitsolution.vinasign.enveloppe.infrastructure.web;

import ca.vinaitsolution.vinasign.enveloppe.application.VerifierJournal;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Preuve d'une enveloppe (VS-071 à VS-073) pour l'application qui l'a créée. */
@RestController
public class PreuveController {

    /** Vue de l'ancrage : fenêtre, empreinte agrégée, heure ; la signature CMS est dans le dossier de preuve (VS-073). */
    public record VueAncrage(Instant depuis, Instant jusqua, String empreinteAgregee, Instant ancreLe, boolean integre) {
    }

    public record VueIntegrite(boolean integre, Long rupture, int evenements, VueAncrage ancrage, Instant verifieeLe) {
    }

    private final VerifierJournal verification;
    private final ca.vinaitsolution.vinasign.enveloppe.application.ProduireCertificat certificat;
    private final ca.vinaitsolution.vinasign.enveloppe.application.ExporterPreuve export;

    public PreuveController(VerifierJournal verification, ca.vinaitsolution.vinasign.enveloppe.application.ProduireCertificat certificat,
                            ca.vinaitsolution.vinasign.enveloppe.application.ExporterPreuve export) {
        this.verification = verification;
        this.certificat = certificat;
        this.export = export;
    }

    /** Certificat de réalisation (VS-072) : PDF/A-2b scellé ; sa production est consignée. */
    @GetMapping(value = "/api/v1/envelopes/{id}/preuve/certificat", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_preuves-lire') or hasAnyRole('preparateur', 'auditeur')")
    public org.springframework.http.ResponseEntity<byte[]> certificat(@PathVariable UUID id) {
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(certificat.executer(new IdentifiantEnveloppe(id)));
    }

    /** Dossier de preuve (VS-073) : archive ZIP au manifeste signé ; l'export est consigné. */
    @GetMapping(value = "/api/v1/envelopes/{id}/preuve/dossier", produces = "application/zip")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_preuves-lire') or hasAnyRole('preparateur', 'auditeur')")
    public org.springframework.http.ResponseEntity<byte[]> dossier(@PathVariable UUID id) {
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Content-Disposition", "attachment; filename=\"preuve-" + id + ".zip\"").body(export.executer(new IdentifiantEnveloppe(id)));
    }

    @GetMapping("/api/v1/envelopes/{id}/preuve/integrite")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_preuves-lire') or hasAnyRole('preparateur', 'auditeur')")
    public VueIntegrite integrite(@PathVariable UUID id) {
        VerifierJournal.Integrite i = verification.executer(new IdentifiantEnveloppe(id));
        VueAncrage ancrage = i.ancrage() == null ? null : new VueAncrage(i.ancrage().depuis(), i.ancrage().jusqua(), i.ancrage().empreinteAgregee(),
                i.ancrage().ancreLe(), i.ancrageIntegre());
        return new VueIntegrite(i.integre(), i.rupture(), i.evenements(), ancrage, i.verifieeLe());
    }
}
