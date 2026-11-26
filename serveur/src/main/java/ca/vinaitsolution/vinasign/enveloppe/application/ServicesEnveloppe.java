package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.PublicationEvenements;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.commun.domain.ContexteAcces;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.enveloppe.application.port.Enveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.domain.ApplicationRequise;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.EnveloppeIntrouvable;
import ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.time.Clock;
import java.util.Map;

/**
 * Ce que partagent les cas d'utilisation des enveloppes : dépôt, journal (et publication aux autres contextes), contexte
 * d'accès, identifiants, horloge. Une
 * application ne voit que ses propres enveloppes ; la RLS limite en plus toute lecture à son organisation.
 */
public record ServicesEnveloppe(Enveloppes enveloppes, JournalEnveloppes journal, ContexteAccesCourant acces, ActeurCourant acteur,
                                GenerateurIdentifiant identifiants, Clock horloge, PublicationEvenements publication) {

    /** Organisation et application de l'appelant ; {@code ApplicationRequise} sinon. */
    public Appelant appelant() {
        ContexteAcces c = acces.courant();
        if (c.organisation().isEmpty() || c.client().isEmpty()) {
            throw new ApplicationRequise();
        }
        return new Appelant(c.organisation().get(), c.client().get());
    }

    /** L'enveloppe de l'appelant ; introuvable pour une autre application (sans révéler qu'elle existe). */
    public Enveloppe trouver(IdentifiantEnveloppe id) {
        Appelant a = appelant();
        return enveloppes.parId(id).filter(e -> e.application().equals(a.application())).orElseThrow(EnveloppeIntrouvable::new);
    }

    public void consigner(Enveloppe e, String type, Map<String, String> donnees) {
        journal.consigner(new EvenementEnveloppe(identifiants.suivant(), e.id(), e.organisation(), type, acteur.courant().reference(),
                horloge.instant(), donnees));
        Map<String, String> attributs = new java.util.HashMap<>(donnees);
        attributs.put("enveloppe", e.id().valeur().toString());
        publication.publier(new EvenementMetier(type, e.organisation(), Map.copyOf(attributs)));
    }

    /** Organisation et application cliente de la requête. */
    public record Appelant(IdentifiantOrganisation organisation, String application) {
    }
}
