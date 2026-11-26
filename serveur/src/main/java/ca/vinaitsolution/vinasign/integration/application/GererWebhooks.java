package ca.vinaitsolution.vinasign.integration.application;

import ca.vinaitsolution.vinasign.commun.application.port.Chiffrement;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.integration.application.port.DestinationsWebhook;
import ca.vinaitsolution.vinasign.integration.application.port.GardeDestinations;
import ca.vinaitsolution.vinasign.integration.application.port.GenerateurSecret;
import ca.vinaitsolution.vinasign.integration.application.port.Livraisons;
import ca.vinaitsolution.vinasign.integration.domain.ApplicationAttendue;
import ca.vinaitsolution.vinasign.integration.domain.DestinationInvalide;
import ca.vinaitsolution.vinasign.integration.domain.DestinationIntrouvable;
import ca.vinaitsolution.vinasign.integration.domain.DestinationWebhook;
import ca.vinaitsolution.vinasign.integration.domain.Livraison;
import ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Destinations des webhooks d'une application (VS-091) : déclarées par elle, gardées contre la SSRF, limitées aux
 * événements publiés (attribut « webhook » du référentiel evenement-enveloppe). Le secret de signature est remis une seule
 * fois, à la création ; VinaSign n'en garde qu'une forme chiffrée.
 */
public class GererWebhooks {

    static final CodeReferentiel EVENEMENTS = new CodeReferentiel("evenement-enveloppe");
    private static final int TRACE_MAX = 100;

    /** Destination créée et son secret, remis une seule fois. */
    public record Creee(DestinationWebhook destination, String secret) {
    }

    /** Sécurité de la destination : garde anti-SSRF, secret aléatoire, chiffrement. */
    public record Securite(GardeDestinations garde, GenerateurSecret secrets, Chiffrement chiffrement) {
    }

    /** Registres lus et écrits ; langue de lecture du référentiel des événements (ses attributs n'en dépendent pas). */
    public record Registres(DestinationsWebhook destinations, Livraisons livraisons, ConsulterReferentiel referentiels, CodeLangue langue) {
    }

    private final Registres registres;
    private final ContexteAccesCourant acces;
    private final Securite securite;
    private final Horodatage outils;

    /** Identifiants et horloge injectés. */
    public record Horodatage(GenerateurIdentifiant identifiants, Clock horloge) {
    }

    public GererWebhooks(Registres registres, ContexteAccesCourant acces, Securite securite, Horodatage outils) {
        this.registres = registres;
        this.acces = acces;
        this.securite = securite;
        this.outils = outils;
    }

    public Creee enregistrer(String url, Set<String> evenements) {
        ContexteAcces c = appelant();
        Set<String> publies = publies();
        Set<String> demandes = evenements == null ? Set.of() : evenements;
        if (!publies.containsAll(demandes)) {
            throw new DestinationInvalide("Événements inconnus : " + demandes.stream().filter(e -> !publies.contains(e)).sorted().toList() + ".");
        }
        securite.garde().exiger(url);
        DestinationWebhook d = new DestinationWebhook(outils.identifiants().suivant(), c.organisation().get().valeur(), c.client().get(), url.strip(), demandes,
                outils.horloge().instant());
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(securite.secrets().nouveau());
        registres.destinations().ajouter(d, Base64.getEncoder().encodeToString(securite.chiffrement().chiffrer(secret)));
        return new Creee(d, secret);
    }

    public List<DestinationWebhook> lister() {
        return registres.destinations().actives(appelant().client().get());
    }

    public void retirer(UUID id) {
        registres.destinations().retirer(sienne(id).id(), outils.horloge().instant());
    }

    /** Trace des dernières livraisons d'une destination (tentatives, code HTTP, livraison ou abandon). */
    public List<Livraison> livraisons(UUID id) {
        return registres.livraisons().de(sienne(id).id(), TRACE_MAX);
    }

    private DestinationWebhook sienne(UUID id) {
        String client = appelant().client().get();
        return registres.destinations().active(id).filter(d -> d.client().equals(client)).orElseThrow(DestinationIntrouvable::new);
    }

    private ContexteAcces appelant() {
        ContexteAcces c = acces.courant();
        if (c.organisation().isEmpty() || c.client().isEmpty()) {
            throw new ApplicationAttendue();
        }
        return c;
    }

    /** Noms de webhook publiés : attribut « webhook » des événements d'enveloppe, langue indifférente. */
    private Set<String> publies() {
        return registres.referentiels().executer(EVENEMENTS, registres.langue()).valeurs().stream()
                .map(v -> v.attributs().get("webhook")).filter(w -> w != null && !w.isBlank()).collect(Collectors.toUnmodifiableSet());
    }
}
