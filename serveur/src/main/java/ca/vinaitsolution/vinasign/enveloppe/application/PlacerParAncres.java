package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.enveloppe.application.port.LocalisationAncres;
import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import java.util.ArrayList;
import java.util.List;

/**
 * Placement des champs par ancres textuelles (VS-063) : l'intégrateur écrit dans son document un texte repère
 * (ex. {@code {{signature:1}}}) ; chaque occurrence devient un champ, dont le coin haut-gauche est celui de l'ancre. Une ancre
 * introuvable est refusée en la nommant ; les règles des champs restent celles de {@link DefinirChamps}.
 */
public class PlacerParAncres {

    static final String ORIGINAL = "original";

    /** Ancre et champ à créer à chacune de ses occurrences ; taille en fraction de la page. */
    public record Demande(String ancre, int ordre, String type, String libelle, double largeur, double hauteur, boolean obligatoire) {
    }

    /** Lecture du document et recherche des ancres. */
    public record Document(VersionsDocuments versions, StockageDocuments stockage, LocalisationAncres localisation) {
    }

    private final ServicesEnveloppe services;
    private final Document document;
    private final DefinirChamps champs;

    public PlacerParAncres(ServicesEnveloppe services, Document document, DefinirChamps champs) {
        this.services = services;
        this.document = document;
        this.champs = champs;
    }

    public List<Champ> executer(IdentifiantEnveloppe id, List<Demande> demandes) {
        Enveloppe e = services.trouver(id);
        VersionDocument v = document.versions().de(id).stream().filter(x -> ORIGINAL.equals(x.nature())).findFirst()
                .orElseThrow(() -> new SaisieInvalide("document-absent", "Déposer le document avant d'y chercher des ancres."));
        byte[] pdf = v.verifier(document.stockage().lire(v.cleObjet()));
        List<DefinirChamps.Demande> resultat = new ArrayList<>();
        for (Demande d : demandes) {
            Signataire s = signataire(e, d.ordre());
            List<LocalisationAncres.Emplacement> trouves = document.localisation().localiser(pdf, d.ancre());
            if (trouves.isEmpty()) {
                throw new SaisieInvalide("ancre-absente", "L'ancre « " + d.ancre() + " » ne figure pas dans le document.");
            }
            trouves.forEach(t -> resultat.add(new DefinirChamps.Demande(s.id(), d.type(), d.libelle(), t.page(), t.x(), t.y(),
                    Math.min(d.largeur(), 1 - t.x()), Math.min(d.hauteur(), 1 - t.y()), d.obligatoire())));
        }
        return champs.executer(id, resultat);
    }

    /** Le signataire de cet ordre ; plusieurs au même ordre (signature en parallèle) : l'ancre est ambiguë. */
    private static Signataire signataire(Enveloppe e, int ordre) {
        List<Signataire> s = e.signataires().stream().filter(x -> x.ordre() == ordre).toList();
        if (s.size() != 1) {
            throw new SaisieInvalide("ordre-ancre", "L'ordre " + ordre + " doit désigner exactement un signataire de l'enveloppe ("
                    + s.size() + " trouvé(s)).");
        }
        return s.get(0);
    }
}
