package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.StatutEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.TransitionEnveloppeInterdite;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Champs d'une enveloppe en brouillon (VS-052) : type validé contre le référentiel {@code type-champ}, signataire de
 * l'enveloppe, page existante du document original s'il est déposé.
 */
public class DefinirChamps {

    /** Champ demandé. */
    public record Demande(UUID signataire, String type, String libelle, int page, double x, double y, double largeur, double hauteur,
                          boolean obligatoire) {
    }

    static final CodeReferentiel TYPES = new CodeReferentiel("type-champ");

    private final ServicesEnveloppe services;
    private final ChampsEnveloppe champs;
    private final VersionsDocuments versions;
    private final ExigerValeurReferentiel referentiel;

    public DefinirChamps(ServicesEnveloppe services, ChampsEnveloppe champs, VersionsDocuments versions, ExigerValeurReferentiel referentiel) {
        this.services = services;
        this.champs = champs;
        this.versions = versions;
        this.referentiel = referentiel;
    }

    /** Champs déjà placés, pour reprendre la préparation d'un brouillon ou relire une enveloppe envoyée. */
    public List<Champ> lire(IdentifiantEnveloppe id) {
        return champs.de(services.trouver(id).id());
    }

    public List<Champ> executer(IdentifiantEnveloppe id, List<Demande> demandes) {
        Enveloppe e = services.trouver(id);
        if (e.statut() != StatutEnveloppe.BROUILLON) {
            throw new TransitionEnveloppeInterdite("Les champs ne changent plus après l'envoi.");
        }
        int pages = versions.de(id).stream().filter(v -> "original".equals(v.nature())).mapToInt(v -> v.pages()).max().orElse(Integer.MAX_VALUE);
        List<UUID> signataires = e.signataires().stream().map(Signataire::id).toList();
        List<Champ> liste = demandes.stream().map(d -> {
            referentiel.executer(TYPES, new CodeReferentiel(d.type()));
            if (!signataires.contains(d.signataire())) {
                throw new SaisieInvalide("signataire-inconnu", "Le champ « " + d.libelle() + " » est assigné à un signataire absent de l'enveloppe.");
            }
            if (d.page() > pages) {
                throw new SaisieInvalide("page-inexistante", "Le champ « " + d.libelle() + " » vise une page au-delà du document (" + pages + " pages).");
            }
            return new Champ(services.identifiants().suivant(), d.signataire(), d.type(), d.libelle(), d.page(), d.x(), d.y(), d.largeur(), d.hauteur(),
                    d.obligatoire());
        }).toList();
        champs.definir(id, liste);
        services.consigner(e, "champs-definis", Map.of("nombre", String.valueOf(liste.size())));
        return liste;
    }
}
