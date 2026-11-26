package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.CompositionDocument;
import ca.vinaitsolution.vinasign.enveloppe.application.port.EnveloppesAFinaliser;
import ca.vinaitsolution.vinasign.enveloppe.application.port.LectureSaisies;
import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Apposition;
import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.Initiales;
import ca.vinaitsolution.vinasign.enveloppe.domain.ModeEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.SignatureApposee;
import ca.vinaitsolution.vinasign.enveloppe.domain.StatutEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finalisation (VS-070), tâche de plateforme : pour chaque enveloppe dont toutes les signatures sont recueillies, le
 * document préparé est relu et son empreinte revérifiée, les signatures et valeurs y sont apposées, le tout est scellé
 * par le cachet et conservé comme version finale immuable ; l'enveloppe n'est complétée qu'ensuite. Une reprise après
 * une interruption réutilise la version finale déjà conservée : jamais de second scellement.
 */
public class FinaliserEnveloppes {

    static final String ACTEUR = "plateforme:finalisation";
    static final String FINAL = "final";
    static final CodeReferentiel TYPES_CHAMP = new CodeReferentiel("type-champ");

    /** Lectures et écritures du document. */
    public record Documents(VersionsDocuments versions, StockageDocuments stockage, ChampsEnveloppe champs, LectureSaisies saisies,
                            CompositionDocument composition, ScellementDocuments scellement) {
    }

    private final EnveloppesAFinaliser aFinaliser;
    private final ExecutionOrganisation execution;
    private final ServicesEnveloppe services;
    private final Documents documents;
    private final ExigerValeurReferentiel referentiel;
    private final int lot;

    public FinaliserEnveloppes(EnveloppesAFinaliser aFinaliser, ExecutionOrganisation execution, ServicesEnveloppe services, Documents documents,
                               ExigerValeurReferentiel referentiel, int lot) {
        this.aFinaliser = aFinaliser;
        this.execution = execution;
        this.services = services;
        this.documents = documents;
        this.referentiel = referentiel;
        this.lot = lot;
    }

    /** Rend le nombre d'enveloppes complétées ; chacune dans sa propre transaction, au nom de son organisation. */
    public int executer() {
        int n = 0;
        for (EnveloppesAFinaliser.AFinaliser a : aFinaliser.aFinaliser(lot)) {
            if (Boolean.TRUE.equals(execution.pour(new IdentifiantOrganisation(a.organisation()), ACTEUR,
                    () -> finaliser(new IdentifiantEnveloppe(a.enveloppe()))))) {
                n++;
            }
        }
        return n;
    }

    boolean finaliser(IdentifiantEnveloppe id) {
        Optional<Enveloppe> trouvee = services.enveloppes().parId(id).filter(e -> e.statut() == StatutEnveloppe.ENVOYEE && e.toutesSignees());
        if (trouvee.isEmpty()) {
            return false;
        }
        Enveloppe e = trouvee.get();
        Map<String, String> donnees = e.mode() == ModeEnveloppe.DOCUMENT ? Map.of("sha256", versionFinale(e).sha256()) : Map.of();
        Enveloppe completee = e.completer(services.horloge().instant());
        services.enveloppes().remplacer(completee);
        services.consigner(completee, "enveloppe-completee", donnees);
        return true;
    }

    /** Version finale déjà conservée (reprise), sinon composée, scellée et conservée maintenant. */
    private VersionDocument versionFinale(Enveloppe e) {
        List<VersionDocument> versions = documents.versions().de(e.id());
        Optional<VersionDocument> existante = versions.stream().filter(v -> FINAL.equals(v.nature())).findFirst();
        if (existante.isPresent()) {
            return existante.get();
        }
        VersionDocument source = versions.stream().max(Comparator.comparing(VersionDocument::creeLe))
                .orElseThrow(() -> new IllegalStateException("Enveloppe « document » sans version."));
        byte[] scelle = documents.scellement().scellerPdf(documents.composition().composer(lireVerifie(source), appositions(e)),
                "VinaSign " + e.id().valeur());
        // Clé propre à chaque tentative : un objet écrit par une tentative interrompue (stockage WORM) ne bloque pas la reprise.
        UUID version = services.identifiants().suivant();
        String cle = "enveloppes/" + e.organisation().valeur() + "/" + e.id().valeur() + "/final-" + version + ".pdf";
        documents.stockage().ecrire(cle, scelle);
        VersionDocument finale = new VersionDocument(version, e.id(), FINAL, cle, sha256(scelle), scelle.length, source.pages(),
                services.horloge().instant());
        documents.versions().ajouter(finale);
        return finale;
    }

    private byte[] lireVerifie(VersionDocument v) {
        return v.verifier(documents.stockage().lire(v.cleObjet()));
    }

    /** Zones de signature ou d'initiales : l'acte de signer ; autres champs : valeur saisie (vide : rien n'est apposé). */
    private List<Apposition> appositions(Enveloppe e) {
        Map<UUID, SignatureApposee> signatures = documents.saisies().signatures(e.id()).stream()
                .collect(Collectors.toMap(SignatureApposee::signataire, Function.identity()));
        Map<UUID, String> valeurs = documents.saisies().valeurs(e.id());
        List<Apposition> appositions = new ArrayList<>();
        for (Champ c : documents.champs().de(e.id())) {
            Map<String, String> type = referentiel.executer(TYPES_CHAMP, new CodeReferentiel(c.type())).attributs();
            if ("signature".equals(type.get("saisie"))) {
                Optional.ofNullable(signatures.get(c.signataire())).map(s -> acte(e, c, s, "initiales".equals(type.get("rendu")))).ifPresent(appositions::add);
            } else if (valeurs.containsKey(c.id())) {
                appositions.add(Apposition.texte(c, valeurs.get(c.id())));
            }
        }
        return appositions;
    }

    /** Initiales du nom tapé, sinon du signataire ; à défaut de nom, comme une zone de signature : image ou nom tapé. */
    private Apposition acte(Enveloppe e, Champ c, SignatureApposee s, boolean initiales) {
        String nom = s.dessinee() ? e.signataires().stream().filter(x -> x.id().equals(s.signataire())).map(Signataire::nom)
                .filter(java.util.Objects::nonNull).findFirst().orElse("") : s.nomSaisi();
        String lettres = initiales ? Initiales.de(nom) : "";
        if (!lettres.isEmpty()) {
            return Apposition.texte(c, lettres);
        }
        return s.dessinee() ? Apposition.image(c, documents.stockage().lire(s.cleImage())) : Apposition.texte(c, s.nomSaisi());
    }

    private static String sha256(byte[] contenu) {
        return HexFormat.of().formatHex(DeposerDocument.empreinte(contenu));
    }

}
