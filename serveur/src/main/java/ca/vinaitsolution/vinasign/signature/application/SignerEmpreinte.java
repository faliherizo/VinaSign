package ca.vinaitsolution.vinasign.signature.application;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.JournalAudit;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.signature.application.port.Cachet;
import java.time.Clock;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Signature d'une empreinte sans le document (VS-034, VS-ADR-005) : l'application transmet le SHA-256 de son document,
 * VinaSign rend un CMS détaché horodaté, vérifiable avec le document d'origine. Rien du contenu n'est reçu ni conservé ;
 * seul le fait est consigné au journal d'audit.
 */
public class SignerEmpreinte {

    /** CMS rendu et niveau atteint. */
    public record Resultat(byte[] cms, boolean horodate) {
    }

    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    static final String ACTION = "empreinte-signee";

    private final Cachet cachet;
    private final OrganisationCourante organisation;
    private final ActeurCourant acteur;
    private final JournalAudit audit;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;

    public SignerEmpreinte(Cachet cachet, OrganisationCourante organisation, ActeurCourant acteur, JournalAudit audit,
                           GenerateurIdentifiant identifiants, Clock horloge) {
        this.cachet = cachet;
        this.organisation = organisation;
        this.acteur = acteur;
        this.audit = audit;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Resultat executer(String sha256Hex) {
        if (sha256Hex == null || !SHA256.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException("Empreinte SHA-256 attendue : 64 caractères hexadécimaux.");
        }
        IdentifiantOrganisation org = organisation.courante().orElseThrow(() -> new IllegalArgumentException("Organisation requise."));
        byte[] cms = cachet.signerEmpreinte(HexFormat.of().parseHex(sha256Hex));
        audit.consigner(new EvenementAudit(identifiants.suivant(), org, acteur.courant(), ACTION, sha256Hex.toLowerCase(), horloge.instant()));
        return new Resultat(cms, "PAdES-B-LTA".equals(cachet.niveauPades()));
    }
}
