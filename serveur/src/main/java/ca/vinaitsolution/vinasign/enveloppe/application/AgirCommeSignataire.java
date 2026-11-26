package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.PreconditionsSignature;
import ca.vinaitsolution.vinasign.commun.application.port.SignataireCourant;
import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.SignaturesSignataires;
import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.EnveloppeIntrouvable;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.Saisie;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ce que fait un signataire authentifié par sa session (VS-050 à VS-052, VS-045) : lire le document, voir ses champs,
 * signer (image dessinée ou nom tapé, champs obligatoires remplis), refuser. Sans consentement, rien ne se signe.
 */
public class AgirCommeSignataire {

    /** Signature : image PNG (dessin au doigt ou au stylet) ou nom tapé au clavier, et valeurs des champs. */
    public record Signature(byte[] image, String nomSaisi, Map<UUID, String> valeurs) {
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    static final int IMAGE_MAX = 300_000;

    private final ServicesEnveloppe services;
    private final SignataireCourant signataire;
    private final PreconditionsSignature preconditions;
    private final ChampsEnveloppe champs;
    private final VersionsDocuments versions;
    private final DeposerDocument documents;
    private final StockageDocuments stockage;
    private final SignaturesSignataires signatures;
    private final ExigerValeurReferentiel referentiel;

    /**
     * Dépôts du signataire : champs, versions, documents, stockage, trace des signatures, et le référentiel des types de
     * champ (quels champs l'acte de signer remplit).
     */
    public record Depots(ChampsEnveloppe champs, VersionsDocuments versions, DeposerDocument documents, StockageDocuments stockage,
                         SignaturesSignataires signatures, ExigerValeurReferentiel referentiel) {
    }

    public AgirCommeSignataire(ServicesEnveloppe services, SignataireCourant signataire, PreconditionsSignature preconditions, Depots depots) {
        this.services = services;
        this.signataire = signataire;
        this.preconditions = preconditions;
        this.champs = depots.champs();
        this.versions = depots.versions();
        this.documents = depots.documents();
        this.stockage = depots.stockage();
        this.signatures = depots.signatures();
        this.referentiel = depots.referentiel();
    }

    private IdentiteSignataire identite() {
        return signataire.courant().orElseThrow(EnveloppeIntrouvable::new);
    }

    private Enveloppe enveloppe(IdentiteSignataire s) {
        return services.enveloppes().parId(new IdentifiantEnveloppe(s.enveloppe())).orElseThrow(EnveloppeIntrouvable::new);
    }

    /** Document original, empreinte recalculée à la lecture (VS-022). */
    public byte[] document() {
        IdentiteSignataire s = identite();
        return versions.de(new IdentifiantEnveloppe(s.enveloppe())).stream().filter(v -> "original".equals(v.nature())).findFirst()
                .map(documents::lireVerifie).orElseThrow(() -> new SaisieInvalide("document-absent", "Cette enveloppe ne porte pas de document."));
    }

    public List<Champ> mesChamps() {
        IdentiteSignataire s = identite();
        return champs.de(new IdentifiantEnveloppe(s.enveloppe())).stream().filter(c -> c.signataire().equals(s.signataire())).toList();
    }

    public void signer(Signature demande) {
        IdentiteSignataire s = identite();
        preconditions.exiger(s);
        Enveloppe e = enveloppe(s);
        IdentifiantEnveloppe id = e.id();
        Map<UUID, String> valeurs = demande.valeurs() == null ? Map.of() : demande.valeurs();
        String mode = mode(demande);
        List<Champ> tous = champs.de(id);
        Saisie.verifier(s.signataire(), tous, valeurs, rempliesParLActe(tous));
        Enveloppe signee = e.signer(s.signataire(), services.horloge().instant());
        String cle = null;
        if ("dessin".equals(mode)) {
            cle = "enveloppes/" + e.organisation().valeur() + "/" + id.valeur() + "/signatures/" + s.signataire() + ".png";
            stockage.ecrire(cle, demande.image());
        }
        signatures.ajouter(services.identifiants().suivant(), id, s.signataire(), mode, cle, "saisie".equals(mode) ? demande.nomSaisi().strip() : null,
                services.horloge().instant());
        champs.enregistrerValeurs(id, s.signataire(), valeurs);
        services.enveloppes().remplacer(signee);
        services.consigner(signee, "signataire-a-signe", Map.of("signataire", s.signataire().toString(), "mode", mode));
        if (signee.toutesSignees()) {
            services.consigner(signee, "signatures-recueillies", Map.of());
        }
    }

    /** Champs dont le type (référentiel {@code type-champ}, attribut {@code saisie}) est rempli par l'acte de signer. */
    private Set<UUID> rempliesParLActe(List<Champ> liste) {
        return liste.stream().filter(c -> "signature".equals(referentiel.executer(DefinirChamps.TYPES, new CodeReferentiel(c.type()))
                .attributs().get("saisie"))).map(Champ::id).collect(Collectors.toSet());
    }

    /** Refus (VS-045) : distinct d'une signature ; termine l'enveloppe ; motif facultatif ; aucun consentement requis. */
    public void refuser(String motif) {
        IdentiteSignataire s = identite();
        Enveloppe refusee = enveloppe(s).refuser(s.signataire(), services.horloge().instant());
        services.enveloppes().remplacer(refusee);
        services.consigner(refusee, "signataire-a-refuse", motif == null || motif.isBlank() ? Map.of("signataire", s.signataire().toString())
                : Map.of("signataire", s.signataire().toString(), "motif", motif));
    }

    /** Dessin (PNG borné) ou nom tapé (alternative au clavier, VS-051) ; jamais les deux, jamais aucun. */
    static String mode(Signature d) {
        boolean dessin = d.image() != null && d.image().length > 0;
        boolean saisie = d.nomSaisi() != null && !d.nomSaisi().isBlank();
        if (dessin == saisie) {
            throw new SaisieInvalide("signature-requise", "Dessiner la signature ou taper son nom (l'un ou l'autre).");
        }
        if (dessin && (d.image().length > IMAGE_MAX || !java.util.Arrays.equals(d.image(), 0, PNG.length, PNG, 0, PNG.length))) {
            throw new SaisieInvalide("signature-image", "L'image de la signature doit être un PNG de 300 Ko au plus.");
        }
        if (saisie && d.nomSaisi().strip().length() > 200) {
            throw new SaisieInvalide("signature-nom", "Le nom tapé dépasse 200 caractères.");
        }
        return dessin ? "dessin" : "saisie";
    }
}
