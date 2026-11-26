package ca.vinaitsolution.vinasign.session.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.commun.infrastructure.securite.AuthentificationSignataire;
import ca.vinaitsolution.vinasign.session.application.AuthentifierParCode;
import ca.vinaitsolution.vinasign.session.application.Consentir;
import ca.vinaitsolution.vinasign.session.application.OuvrirSession;
import ca.vinaitsolution.vinasign.session.application.dto.VueSession;
import ca.vinaitsolution.vinasign.session.domain.Consentement;
import ca.vinaitsolution.vinasign.session.domain.LienResolu;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parcours du signataire (VS-040, VS-041). Le jeton du lien voyage dans le corps (jamais dans une URL d'API journalisée) ;
 * l'ouverture rend une session de courte durée, présentée ensuite dans l'en-tête {@code X-Session-Signataire}.
 */
@RestController
@RequestMapping("/api/v1/signataire")
public class SignataireController {

    /** Jeton reçu dans le lien d'invitation. */
    public record Ouverture(@NotBlank @Size(max = 100) String jeton) {
    }

    /** Version du texte de consentement affiché et accepté. */
    public record Acceptation(@NotBlank @Size(max = 60) String version) {
    }

    /** Preuve rendue : version et empreinte du texte accepté, heure d'acceptation. */
    public record VueConsentement(String version, String empreinteTexte, Instant accepteLe) {
    }

    /** Canal du code (référentiel canal-code). */
    public record DemandeCode(@NotBlank @Size(max = 30) String canal) {
    }

    /** Code reçu. */
    public record SaisieCode(@NotBlank @Size(max = 12) String code) {
    }

    private final OuvrirSession ouverture;
    private final Consentir consentement;
    private final AuthentifierParCode code;
    private final TransactionTemplate transaction;

    public SignataireController(OuvrirSession ouverture, Consentir consentement, AuthentifierParCode code, TransactionTemplate transaction) {
        this.ouverture = ouverture;
        this.consentement = consentement;
        this.code = code;
        this.transaction = transaction;
    }

    private static AuthentifierParCode.Signataire signataire() {
        AuthentificationSignataire a = (AuthentificationSignataire) SecurityContextHolder.getContext().getAuthentication();
        return new AuthentifierParCode.Signataire(a.organisation().valeur(), a.enveloppe(), a.signataire(), a.session());
    }

    /** Toujours 202 : la réponse ne dit pas si le canal est joignable (aucune énumération). */
    @PostMapping("/code")
    public org.springframework.http.ResponseEntity<Void> demanderCode(@Valid @RequestBody DemandeCode corps) {
        transaction.executeWithoutResult(etat -> code.demander(signataire(), corps.canal()));
        return org.springframework.http.ResponseEntity.accepted().build();
    }

    @PostMapping("/code/verification")
    public AuthentifierParCode.Verification verifierCode(@Valid @RequestBody SaisieCode corps) {
        return transaction.execute(etat -> code.verifier(signataire(), corps.code()));
    }

    /**
     * Le lien est d'abord retrouvé hors de toute organisation ; le signataire est ensuite authentifié dans l'organisation
     * de l'enveloppe, puis l'ouverture se fait dans une transaction soumise à la RLS de celle-ci.
     */
    @PostMapping("/ouverture")
    public VueSession ouvrir(@Valid @RequestBody Ouverture corps) {
        LienResolu lien = ouverture.resoudre(corps.jeton());
        SecurityContextHolder.getContext().setAuthentication(new AuthentificationSignataire(new IdentifiantOrganisation(lien.organisation()),
                lien.lien().enveloppe(), lien.lien().signataire(), null));
        return transaction.execute(etat -> ouverture.ouvrir(lien));
    }

    @PostMapping("/consentement")
    public VueConsentement consentir(@AuthenticationPrincipal String acteur, @Valid @RequestBody Acceptation corps) {
        AuthentificationSignataire a = (AuthentificationSignataire) SecurityContextHolder.getContext().getAuthentication();
        Consentement c = transaction.execute(etat -> consentement.executer(a.enveloppe(), a.signataire(), corps.version()));
        return new VueConsentement(c.versionTexte(), c.empreinteTexte(), c.accepteLe());
    }
}
