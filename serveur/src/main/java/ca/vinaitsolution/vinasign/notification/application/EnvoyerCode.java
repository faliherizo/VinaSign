package ca.vinaitsolution.vinasign.notification.application;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteSms;
import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Envoi d'un code à usage unique (VS-042) par le canal demandé : courriel, ou SMS (VS-081). Un canal que le signataire
 * n'a pas (pas de courriel, pas de téléphone) ne produit rien, en silence : la réponse au signataire est la même (aucune
 * énumération).
 */
public class EnvoyerCode implements AbonneEvenements {

    static final String COURRIEL = "courriel";
    static final String SMS = "sms";

    private final BoiteEnvoi boite;
    private final BoiteSms boiteSms;
    private final LectureEnveloppes enveloppes;
    private final ModelesCourriel modeles;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;

    public EnvoyerCode(BoiteEnvoi boite, BoiteSms boiteSms, LectureEnveloppes enveloppes, ModelesCourriel modeles, GenerateurIdentifiant identifiants,
                       Clock horloge) {
        this.boite = boite;
        this.boiteSms = boiteSms;
        this.enveloppes = enveloppes;
        this.modeles = modeles;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    @Override
    public boolean concerne(String type) {
        return "code-a-envoyer".equals(type);
    }

    @Override
    public void recevoir(EvenementMetier e) {
        UUID signataire = UUID.fromString(e.attributs().get("signataire"));
        Optional<DossierEnveloppe.SignataireDossier> trouve = enveloppes.dossier(UUID.fromString(e.attributs().get("enveloppe")))
                .flatMap(d -> d.signataires().stream().filter(x -> x.id().equals(signataire)).findFirst());
        String canal = e.attributs().get("canal");
        if (SMS.equals(canal)) {
            trouve.filter(x -> x.telephone() != null).ifPresent(x -> boiteSms.deposer(identifiants.suivant(), e.organisation().valeur(), x.telephone(),
                    modeles.rendre("code-sms", x.langue(), Map.of("code", e.attributs().get("code"))).corps(), horloge.instant()));
            return;
        }
        Optional<DossierEnveloppe.SignataireDossier> s = trouve.filter(x -> COURRIEL.equals(canal) && x.courriel() != null);
        s.ifPresent(x -> {
            ModelesCourriel.Rendu r = modeles.rendre("code", x.langue(), Map.of("nom", x.nom(), "code", e.attributs().get("code")));
            boite.deposer(new CourrielSortant(identifiants.suivant(), e.organisation().valeur(), x.courriel(), r.sujet(), r.corps(), 0, horloge.instant()));
        });
    }
}
