package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.enveloppe.application.dto.VueDepot;
import ca.vinaitsolution.vinasign.enveloppe.application.dto.VueEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.AnalyseAntivirus;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ConversionPdfA;
import ca.vinaitsolution.vinasign.enveloppe.application.port.DepotsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.InspectionPdf;
import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.DepotDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.DocumentRefuse;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.ModeEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import ca.vinaitsolution.vinasign.enveloppe.domain.StatutEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.TransitionEnveloppeInterdite;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Dépôt d'un PDF par URL présignée (VS-021) puis confirmation, qui crée la version originale immuable (VS-022). À la
 * confirmation : type réel (signature %PDF), taille, structure lisible, antivirus ; tout refus est explicite et typé.
 */
public class DeposerDocument {

    /** Réglages : durée de validité d'une URL, taille maximale, préfixe de l'URL de dépôt. */
    public record Reglages(Duration validite, long tailleMax, String prefixeUrl) {
    }

    static final String ORIGINAL = "original";
    private static final byte[] SIGNATURE_PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] SIGNATURE_ZIP = {'P', 'K', 3, 4};

    private final ServicesEnveloppe services;
    private final DepotsDocuments depots;
    private final StockageDocuments stockage;
    private final VersionsDocuments versions;
    private final Controles controles;
    private final Reglages reglages;
    private final SecureRandom alea;

    /** Contrôles du contenu déposé, et conversion des formats bureautiques (VS-035). */
    public record Controles(InspectionPdf pdf, AnalyseAntivirus antivirus, ConversionPdfA conversion) {
    }

    public DeposerDocument(ServicesEnveloppe services, DepotsDocuments depots, StockageDocuments stockage, VersionsDocuments versions,
                           Controles controles, Reglages reglages, SecureRandom alea) {
        this.services = services;
        this.depots = depots;
        this.stockage = stockage;
        this.versions = versions;
        this.controles = controles;
        this.reglages = reglages;
        this.alea = alea;
    }

    public VueDepot demander(IdentifiantEnveloppe id) {
        Enveloppe e = services.trouver(id);
        if (e.statut() != StatutEnveloppe.BROUILLON) {
            throw new TransitionEnveloppeInterdite("On ne dépose un document que dans une enveloppe en brouillon.");
        }
        if (e.mode() != ModeEnveloppe.DOCUMENT) {
            throw new SaisieInvalide("mode-sans-document", "Une enveloppe en mode empreinte ne reçoit pas de document.");
        }
        byte[] jeton = new byte[32];
        alea.nextBytes(jeton);
        String texte = Base64.getUrlEncoder().withoutPadding().encodeToString(jeton);
        UUID depot = services.identifiants().suivant();
        Instant expire = services.horloge().instant().plus(reglages.validite());
        depots.ajouter(new DepotDocument(depot, id, HexFormat.of().formatHex(empreinte(texte.getBytes(java.nio.charset.StandardCharsets.US_ASCII))),
                "depots/" + e.organisation().valeur() + "/" + depot, expire, null, null));
        return new VueDepot(depot, reglages.prefixeUrl() + texte, expire);
    }

    /** Réception par l'URL présignée : l'appelant n'est authentifié que par le jeton ; un seul objet, avant l'échéance. */
    public void recevoir(String jeton, byte[] contenu) {
        if (contenu.length == 0 || contenu.length > reglages.tailleMax()) {
            throw new DocumentRefuse("document-taille", "Document vide ou au-delà de la taille maximale (" + reglages.tailleMax() + " octets).");
        }
        DepotDocument d = depots.recevoir(HexFormat.of().formatHex(empreinte(jeton.getBytes(java.nio.charset.StandardCharsets.US_ASCII))), services.horloge().instant())
                .orElseThrow(() -> new DocumentRefuse("depot-invalide", "URL de dépôt inconnue, expirée ou déjà utilisée."));
        stockage.ecrire(d.cleObjet(), contenu);
    }

    public VueEnveloppe confirmer(IdentifiantEnveloppe id, UUID depotId) {
        Enveloppe e = services.trouver(id);
        DepotDocument d = depots.parId(depotId).filter(x -> x.enveloppe().equals(id) && x.recuLe() != null && x.confirmeLe() == null)
                .orElseThrow(() -> new DocumentRefuse("depot-non-recu", "Aucun document reçu à confirmer pour ce dépôt."));
        byte[] depose = stockage.lire(d.cleObjet());
        analyser(depose);
        byte[] contenu = enPdf(depose);
        int pages = controles.pdf().pages(contenu);
        String cle = "enveloppes/" + e.organisation().valeur() + "/" + id.valeur() + "/" + ORIGINAL;
        stockage.ecrire(cle, contenu);
        String sha = HexFormat.of().formatHex(empreinte(contenu));
        Instant maintenant = services.horloge().instant();
        versions.ajouter(new VersionDocument(services.identifiants().suivant(), id, ORIGINAL, cle, sha, contenu.length, pages, maintenant));
        depots.confirmer(depotId, maintenant);
        services.consigner(e, "document-depose", Map.of("nature", ORIGINAL, "sha256", sha));
        return VueEnveloppe.de(e, versions.de(id));
    }

    /** Un PDF est retenu tel quel ; un document bureautique (conteneur ZIP : Word, OpenDocument) est converti en PDF/A. */
    private byte[] enPdf(byte[] depose) {
        if (commencePar(depose, SIGNATURE_PDF)) {
            return depose;
        }
        if (commencePar(depose, SIGNATURE_ZIP)) {
            byte[] pdf = controles.conversion().convertir(depose, "document.docx");
            analyser(pdf);
            return pdf;
        }
        throw new DocumentRefuse("document-type", "Le document n'est ni un PDF ni un document bureautique (Word, OpenDocument).");
    }

    private void analyser(byte[] contenu) {
        controles.antivirus().analyser(contenu).ifPresent(menace -> {
            throw new DocumentRefuse("document-infecte", "Le document est refusé par l'analyse antivirale.");
        });
    }

    /** Lecture critique (VS-022) : l'empreinte est recalculée ; un écart arrête tout. */
    public byte[] lireVerifie(VersionDocument version) {
        return version.verifier(stockage.lire(version.cleObjet()));
    }

    private static boolean commencePar(byte[] contenu, byte[] debut) {
        if (contenu.length < debut.length) {
            return false;
        }
        for (int i = 0; i < debut.length; i++) {
            if (contenu[i] != debut[i]) {
                return false;
            }
        }
        return true;
    }

    static byte[] empreinte(byte[] contenu) {
        return ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256(contenu);
    }
}
