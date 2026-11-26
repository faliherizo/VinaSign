package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.ArchivageLongTerme;
import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.Condensats;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.enveloppe.application.port.AlertesArchive;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ArchivesDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.EtatArchive;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Validité à long terme (VS-132), tâche de plateforme : le dernier état archivé de chaque document final dont l'horodatage
 * expire avant la marge reçoit des données de validation à jour et un nouvel horodatage de document ; le nouvel état est
 * conservé (stockage immuable, clé propre) et consigné au journal. Un document final encore jamais évalué l'est d'abord :
 * son échéance est notée, il ne sera renouvelé qu'à l'approche de celle-ci. Un échec n'arrête pas le lot : il est signalé
 * à l'exploitation et repris au passage suivant.
 */
public class RenouvelerArchives {

    static final String ACTEUR = "plateforme:archivage";
    static final String EVENEMENT = "archive-renouvelee";

    /** Lectures et écritures des documents archivés. */
    public record Documents(ArchivesDocuments archives, VersionsDocuments versions, StockageDocuments stockage, ArchivageLongTerme archivage) {
    }

    /** Exécution par organisation, alerte, marge avant l'échéance et taille du lot. */
    public record Exploitation(ExecutionOrganisation execution, AlertesArchive alertes, Duration marge, int lot) {
    }

    private final Documents documents;
    private final ServicesEnveloppe services;
    private final Exploitation exploitation;

    public RenouvelerArchives(Documents documents, ServicesEnveloppe services, Exploitation exploitation) {
        this.documents = documents;
        this.services = services;
        this.exploitation = exploitation;
    }

    /** Rend le nombre de documents renouvelés. */
    public int executer() {
        Instant limite = services.horloge().instant().plus(exploitation.marge());
        int n = 0;
        for (ArchivesDocuments.ARenouveler a : documents.archives().aRenouveler(limite, exploitation.lot())) {
            IdentifiantEnveloppe id = new IdentifiantEnveloppe(a.enveloppe());
            try {
                if (Boolean.TRUE.equals(exploitation.execution().pour(new IdentifiantOrganisation(a.organisation()), ACTEUR, () -> traiter(id, limite)))) {
                    n++;
                }
            } catch (RuntimeException e) {
                exploitation.alertes().echec(id, e);
            }
        }
        return n;
    }

    boolean traiter(IdentifiantEnveloppe id, Instant limite) {
        EtatArchive courant = documents.archives().dernier(id).orElseGet(() -> evaluer(id));
        if (!courant.echoitAvant(limite)) {
            return false;
        }
        byte[] actuel = courant.verifier(documents.stockage().lire(courant.cleObjet()));
        byte[] renouvele = documents.archivage().renouveler(actuel);
        UUID etat = services.identifiants().suivant();
        Enveloppe e = services.enveloppes().parId(id).orElseThrow(() -> new IllegalStateException("Enveloppe archivée introuvable."));
        String cle = "enveloppes/" + e.organisation().valeur() + "/" + id.valeur() + "/archive-" + etat + ".pdf";
        documents.stockage().ecrire(cle, renouvele);
        Instant echeance = documents.archivage().echeance(renouvele).orElseThrow(() -> new IllegalStateException("Renouvellement sans horodatage."));
        String sha256 = hex(renouvele);
        documents.archives().ajouter(new EtatArchive(etat, id, cle, sha256, renouvele.length, echeance, services.horloge().instant()));
        services.consigner(e, EVENEMENT, Map.of("sha256", sha256, "echeance", echeance.toString()));
        return true;
    }

    /** Premier passage : le document final devient le premier état archivé, avec l'échéance de son horodatage. */
    private EtatArchive evaluer(IdentifiantEnveloppe id) {
        VersionDocument finale = documents.versions().de(id).stream().filter(v -> FinaliserEnveloppes.FINAL.equals(v.nature())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Enveloppe terminée sans version finale."));
        byte[] contenu = finale.verifier(documents.stockage().lire(finale.cleObjet()));
        EtatArchive premier = new EtatArchive(services.identifiants().suivant(), id, finale.cleObjet(), finale.sha256(), finale.taille(),
                documents.archivage().echeance(contenu).orElse(null), services.horloge().instant());
        documents.archives().ajouter(premier);
        return premier;
    }

    private static String hex(byte[] contenu) {
        return Condensats.sha256Hex(contenu);
    }
}
