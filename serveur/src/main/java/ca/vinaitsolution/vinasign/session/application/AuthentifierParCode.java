package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.PublicationEvenements;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.session.application.port.DefisCode;
import ca.vinaitsolution.vinasign.session.application.port.EmpreinteCodes;
import ca.vinaitsolution.vinasign.session.domain.CodeRefuse;
import ca.vinaitsolution.vinasign.session.domain.DefiCode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Authentification renforcée par code (VS-042), porté du portail du CLM : un code à six chiffres envoyé par le canal
 * choisi (référentiel {@code canal-code}) ; cinq essais puis verrouillage ; à usage unique. La demande rend toujours la
 * même réponse, que le canal soit joignable ou non : aucune énumération.
 */
public class AuthentifierParCode {

    /** Signataire authentifié par sa session. */
    public record Signataire(UUID organisation, UUID enveloppe, UUID signataire, UUID session) {
    }

    /** Issue d'une vérification : validé, ou essais restants. */
    public record Verification(boolean valide, int essaisRestants) {
    }

    static final CodeReferentiel CANAUX = new CodeReferentiel("canal-code");
    static final String RENFORCE = "renforce";
    static final int ESSAIS = 5;

    private final DefisCode defis;
    private final EmpreinteCodes empreintes;
    private final ExigerValeurReferentiel referentiel;
    private final PublicationEvenements publication;
    private final ServicesSession services;
    private final Duration validite;

    public AuthentifierParCode(DefisCode defis, EmpreinteCodes empreintes, ExigerValeurReferentiel referentiel, PublicationEvenements publication,
                               ServicesSession services, Duration validite) {
        this.defis = defis;
        this.empreintes = empreintes;
        this.referentiel = referentiel;
        this.publication = publication;
        this.services = services;
        this.validite = validite;
    }

    public void demander(Signataire s, String canal) {
        referentiel.executer(CANAUX, new CodeReferentiel(canal));
        String code = String.format("%06d", services.alea().nextInt(1_000_000));
        UUID id = services.identifiants().suivant();
        defis.ajouter(DefiCode.emettre(id, s.session(), canal, empreintes.empreinte(id, code), services.horloge().instant().plus(validite), ESSAIS));
        publication.publier(new EvenementMetier("code-a-envoyer", new IdentifiantOrganisation(s.organisation()),
                Map.of("enveloppe", s.enveloppe().toString(), "signataire", s.signataire().toString(), "canal", canal, "code", code)));
    }

    public Verification verifier(Signataire s, String code) {
        DefiCode defi = defis.dernier(s.session())
                .orElseThrow(() -> new CodeRefuse("code-absent", "Aucun code demandé : demander un code."));
        DefiCode suivant = defi.tenter(empreintes.empreinte(defi.id(), code == null ? "" : code), services.horloge().instant());
        defis.remplacer(suivant);
        if (suivant.valideLe() != null) {
            defis.elever(s.session(), RENFORCE);
            return new Verification(true, suivant.essaisRestants());
        }
        return new Verification(false, suivant.essaisRestants());
    }
}
