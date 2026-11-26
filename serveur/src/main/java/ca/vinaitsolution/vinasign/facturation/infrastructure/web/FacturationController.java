package ca.vinaitsolution.vinasign.facturation.infrastructure.web;

import ca.vinaitsolution.vinasign.facturation.application.GererAbonnement;
import ca.vinaitsolution.vinasign.facturation.domain.Abonnement;
import ca.vinaitsolution.vinasign.facturation.domain.Facture;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facturation (VS-121) : l'administrateur de l'organisation consulte son abonnement et ses factures, part payer un plan ou
 * gérer son abonnement chez le prestataire ; le prestataire livre ses événements signés.
 */
@RestController
public class FacturationController {

    public record Paiement(@NotBlank @Size(max = 60) String plan, @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?") String langue) {
    }

    public record Redirection(String url) {
    }

    /** Situation : plan payé et état de l'abonnement (s'il existe), factures hébergées par le prestataire. */
    public record VueFacturation(String plan, String etat, List<VueFacture> factures) {
    }

    public record VueFacture(String numero, long montant, String devise, String url, String pdf, Instant debut, Instant fin, Instant payeeLe) {

        static VueFacture de(Facture f) {
            return new VueFacture(f.numero(), f.montant(), f.devise(), f.urlHebergee(), f.urlPdf(), f.debutPeriode(), f.finPeriode(), f.payeeLe());
        }
    }

    private final GererAbonnement abonnement;

    public FacturationController(GererAbonnement abonnement) {
        this.abonnement = abonnement;
    }

    @GetMapping("/api/v1/billing")
    @PreAuthorize("hasRole('administrateur-organisation')")
    @Transactional(readOnly = true)
    public VueFacturation consulter() {
        GererAbonnement.Situation s = abonnement.consulter();
        return new VueFacturation(s.abonnement().map(Abonnement::plan).orElse(null), s.abonnement().map(a -> a.etat().code()).orElse(null),
                s.factures().stream().map(VueFacture::de).toList());
    }

    @PostMapping("/api/v1/billing/checkout")
    @PreAuthorize("hasRole('administrateur-organisation')")
    @Transactional(readOnly = true)
    public Redirection payer(@Valid @RequestBody Paiement p) {
        return new Redirection(abonnement.payer(p.plan(), p.langue()));
    }

    @PostMapping("/api/v1/billing/portal")
    @PreAuthorize("hasRole('administrateur-organisation')")
    @Transactional(readOnly = true)
    public Redirection gerer() {
        return new Redirection(abonnement.gerer());
    }

    @PostMapping(value = "/api/v1/billing/events", consumes = "application/json")
    public ResponseEntity<Void> evenement(@RequestBody byte[] corps, @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        abonnement.recevoir(corps, signature);
        return ResponseEntity.noContent().build();
    }
}
