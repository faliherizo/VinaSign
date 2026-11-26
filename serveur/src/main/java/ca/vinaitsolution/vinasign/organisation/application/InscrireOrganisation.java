package ca.vinaitsolution.vinasign.organisation.application;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.PublicationEvenements;
import ca.vinaitsolution.vinasign.commun.domain.Condensats;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.organisation.application.dto.VueInscription;
import ca.vinaitsolution.vinasign.organisation.application.port.Inscriptions;
import ca.vinaitsolution.vinasign.organisation.application.port.ResolutionDns;
import ca.vinaitsolution.vinasign.organisation.domain.Inscription;
import ca.vinaitsolution.vinasign.organisation.domain.InscriptionFermee;
import ca.vinaitsolution.vinasign.organisation.domain.InscriptionIntrouvable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Inscription en libre-service (VS-122). Le demandeur reçoit un lien de confirmation (jeton de 256 bits dont seule
 * l'empreinte est conservée) ; selon la politique de la plateforme, il prouve son domaine par un enregistrement TXT, puis
 * l'administrateur de la plateforme valide. Acceptée, la demande devient une organisation du même identifiant, dont le
 * demandeur est invité comme administrateur. Tout s'exécute au nom de cet identifiant : le courriel sortant y est rattaché.
 * Une seconde demande pour un courriel déjà en attente ne produit rien de plus (aucune énumération, aucun envoi multiplié).
 */
public class InscrireOrganisation {

    static final String ACTEUR = "plateforme:inscription";
    static final String A_CONFIRMER = "inscription-a-confirmer";
    static final String INSCRITE = "organisation-inscrite";

    /** Dépôts et services : demandes, DNS, exécution au nom de la future organisation, publication. */
    public record Ports(Inscriptions inscriptions, ResolutionDns dns, ExecutionOrganisation execution, PublicationEvenements publication) {
    }

    /** Identifiants, aléa des jetons, horloge. */
    public record Outils(GenerateurIdentifiant identifiants, SecureRandom alea, Clock horloge) {
    }

    /** Politique (null : inscription fermée), durée du lien, adresse de la page de confirmation. */
    public record Reglages(Inscription.Politique politique, Duration dureeLien, String urlConfirmation) {
    }

    private final Ports ports;
    private final Outils outils;
    private final AdministrerOrganisations organisations;
    private final Reglages reglages;

    public InscrireOrganisation(Ports ports, Outils outils, AdministrerOrganisations organisations, Reglages reglages) {
        this.ports = ports;
        this.outils = outils;
        this.organisations = organisations;
        this.reglages = reglages;
    }

    /** Demande publique ; la réponse est la même que le courriel soit nouveau ou déjà en attente. */
    public void demander(String nom, String courriel, String domaine, String langue) {
        Inscription.Politique politique = politique();
        Instant maintenant = outils.horloge().instant();
        String cle = courriel == null ? "" : courriel.strip();
        var ouverte = ports.inscriptions().ouverteDe(cle);
        if (ouverte.isPresent() && !expiree(ouverte.get(), maintenant)) {
            return;
        }
        ouverte.ifPresent(o -> ports.inscriptions().remplacer(o.refuser("Lien de confirmation expiré.", maintenant)));
        String jeton = aleatoire(32);
        Inscription i = Inscription.demander(outils.identifiants().suivant(), new Inscription.Demande(nom, courriel, domaine, langue,
                Condensats.sha256Hex(jeton), maintenant.plus(reglages.dureeLien()), "vinasign-" + HexFormat.of().formatHex(octets(16))), politique, maintenant);
        auNomDe(i, () -> {
            ports.inscriptions().ajouter(i);
            ports.publication().publier(new EvenementMetier(A_CONFIRMER, new IdentifiantOrganisation(i.id()), Map.of("courriel", i.courriel(),
                    "langue", i.langue(), "organisation", i.nomOrganisation(), "lien", reglages.urlConfirmation() + "?jeton=" + jeton)));
            return null;
        });
    }

    /** Le demandeur ouvre son lien : courriel confirmé, étape suivante (acceptation immédiate au bac à sable). */
    public VueInscription confirmer(String jeton) {
        Inscription i = parJeton(jeton);
        return avancer(i.confirmerCourriel(politique(), outils.horloge().instant()));
    }

    /** Le demandeur consulte l'étape de sa demande, et l'enregistrement DNS à publier. */
    public VueInscription consulter(String jeton) {
        return VueInscription.de(parJeton(jeton));
    }

    /** Le demandeur a publié l'enregistrement TXT : il est cherché sous le domaine. */
    public VueInscription prouverDomaine(String jeton) {
        Inscription i = parJeton(jeton);
        boolean trouve = i.nomEnregistrement() != null && ports.dns().textes(i.nomEnregistrement()).contains(i.defiDns());
        return avancer(i.prouverDomaine(trouve, politique(), outils.horloge().instant()));
    }

    public List<VueInscription> aValider() {
        return ports.inscriptions().aValider().stream().map(VueInscription::de).toList();
    }

    public VueInscription valider(UUID id) {
        return avancer(trouver(id).valider(outils.horloge().instant()));
    }

    public VueInscription refuser(UUID id, String motif) {
        return avancer(trouver(id).refuser(motif, outils.horloge().instant()));
    }

    private VueInscription avancer(Inscription suivante) {
        auNomDe(suivante, () -> {
            ports.inscriptions().remplacer(suivante);
            if (suivante.estAcceptee()) {
                IdentifiantOrganisation org = new IdentifiantOrganisation(suivante.id());
                organisations.inscrire(org, suivante.nomOrganisation());
                ports.publication().publier(new EvenementMetier(INSCRITE, org, Map.of("courriel", suivante.courriel())));
            }
            return null;
        });
        return VueInscription.de(suivante);
    }

    private <T> T auNomDe(Inscription i, Supplier<T> action) {
        return ports.execution().pour(new IdentifiantOrganisation(i.id()), ACTEUR, action);
    }

    private Inscription parJeton(String jeton) {
        if (jeton == null || jeton.isBlank() || jeton.length() > 100) {
            throw new InscriptionIntrouvable();
        }
        return ports.inscriptions().parEmpreinteJeton(Condensats.sha256Hex(jeton)).orElseThrow(InscriptionIntrouvable::new);
    }

    private Inscription trouver(UUID id) {
        return ports.inscriptions().parId(id).orElseThrow(InscriptionIntrouvable::new);
    }

    private Inscription.Politique politique() {
        if (reglages.politique() == null) {
            throw new InscriptionFermee();
        }
        return reglages.politique();
    }

    private static boolean expiree(Inscription i, Instant maintenant) {
        return i.etat() == Inscription.Etat.COURRIEL_A_CONFIRMER && !maintenant.isBefore(i.jetonExpireLe());
    }

    private String aleatoire(int n) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets(n));
    }

    private byte[] octets(int n) {
        byte[] b = new byte[n];
        outils.alea().nextBytes(b);
        return b;
    }
}
