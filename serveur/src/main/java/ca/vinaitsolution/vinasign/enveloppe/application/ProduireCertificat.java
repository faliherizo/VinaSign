package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.LecturePreuvesSignataires;
import ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments;
import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.enveloppe.application.dto.Certificat;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ConversionPdfA;
import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.RenduCertificat;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.dto.VueValeur;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Certificat de réalisation (VS-072) : chronologie, signataires (consentement, niveau d'authentification, méthodes
 * attestées), empreintes des versions du document et intégrité du journal ; mis en page, converti en PDF/A-2b, puis scellé
 * par le cachet. Sa production est consignée au journal.
 */
public class ProduireCertificat {

    static final String TEXTES = "texte-certificat";

    /** Sources de la preuve : versions, journal et son intégrité, ce que les sessions savent des signataires. */
    public record Sources(VersionsDocuments versions, JournalEnveloppes journal, VerifierJournal integrite, LecturePreuvesSignataires signataires) {
    }

    /** Libellés, mise en page, conversion et sceau. */
    public record Fabrication(ConsulterReferentiel referentiels, RenduCertificat rendu, ConversionPdfA conversion, ScellementDocuments scellement,
                              CodeLangue langue) {
    }

    private final ServicesEnveloppe services;
    private final Sources sources;
    private final Fabrication fabrication;

    public ProduireCertificat(ServicesEnveloppe services, Sources sources, Fabrication fabrication) {
        this.services = services;
        this.sources = sources;
        this.fabrication = fabrication;
    }

    public byte[] executer(IdentifiantEnveloppe id) {
        Enveloppe e = services.trouver(id);
        byte[] pdf = produire(e);
        services.consigner(e, "certificat-produit", Map.of());
        return pdf;
    }

    /** Sans contrôle d'appelant ni consignation : pour le dossier de preuve, qui consigne son propre export. */
    byte[] produire(Enveloppe e) {
        Certificat c = assembler(e);
        byte[] pdfA = fabrication.conversion().convertir(fabrication.rendu().html(c), "certificat.html");
        return fabrication.scellement().scellerPdf(pdfA, c.textes().getOrDefault("titre", "") + " " + e.referenceExterne());
    }

    Certificat assembler(Enveloppe e) {
        Map<String, LecturePreuvesSignataires.PreuveSignataire> preuves = sources.signataires().de(e.id().valeur()).stream()
                .collect(Collectors.toMap(p -> p.signataire().toString(), Function.identity()));
        VerifierJournal.Integrite i = sources.integrite().verifier(e.id());
        return new Certificat(fabrication.langue().valeur(), libelles(TEXTES), e.id().valeur().toString(), e.referenceExterne(),
                libelle("statut-enveloppe", e.statut().code()), e.creeLe(),
                sources.versions().de(e.id()).stream().map(v -> new Certificat.Document(libelle("nature-document", v.nature()), v.sha256())).toList(),
                e.signataires().stream().sorted(Comparator.comparingInt(Signataire::ordre)).map(s -> signataire(s, preuves.get(s.id().toString()))).toList(),
                sources.journal().de(e.id()).stream().map(ev -> new Certificat.Evenement(ev.survenuLe(), libelle("evenement-enveloppe", ev.type()), ev.acteur()))
                        .toList(),
                new Certificat.Integrite(i.integre(), i.evenements(), i.ancrage() == null ? null : i.ancrage().empreinteAgregee(),
                        i.ancrage() == null ? null : i.ancrage().ancreLe()),
                services.horloge().instant());
    }

    private Certificat.Signataire signataire(Signataire s, LecturePreuvesSignataires.PreuveSignataire p) {
        boolean consenti = p != null && p.versionConsentement() != null;
        return new Certificat.Signataire(s.ordre(), s.nom(), libelle("statut-signataire", s.statut().code()), s.agiLe(),
                consenti ? p.versionConsentement() + " · " + p.empreinteConsentement() : null, consenti ? p.consentiLe() : null,
                p == null || p.niveau() == null ? null : libelle("niveau-authentification", p.niveau()), p == null ? List.of() : p.methodesAttestees());
    }

    /** Texte fixe du référentiel texte-certificat, dans la langue du certificat (ex. le LISEZMOI du dossier de preuve). */
    String texte(String code) {
        return libelle(TEXTES, code);
    }

    private Map<String, String> libelles(String referentiel) {
        return fabrication.referentiels().executer(new CodeReferentiel(referentiel), fabrication.langue()).valeurs().stream()
                .collect(Collectors.toMap(VueValeur::code, VueValeur::libelle));
    }

    /** Libellé d'un code dans la langue du certificat ; le code lui-même s'il n'est pas (ou plus) au référentiel. */
    private String libelle(String referentiel, String code) {
        return libelles(referentiel).getOrDefault(code, code);
    }

}
