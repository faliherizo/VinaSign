package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.enveloppe.application.dto.VueEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Champ;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.ZonesSignatureParDefaut;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Envoi, annulation et consultation d'une enveloppe par son application (VS-024, VS-025). */
public class PiloterEnveloppe {

    static final String ORIGINAL = "original";
    /** Valeur initiale du référentiel {@code type-champ} posée par défaut, désignée par son code. */
    static final CodeReferentiel ZONE_SIGNATURE = new CodeReferentiel("signature");
    private static final int LIBELLE_MAX = 120;

    private final ServicesEnveloppe services;
    private final VersionsDocuments versions;
    private final ChampsEnveloppe champs;
    private final ExigerValeurReferentiel referentiel;

    public PiloterEnveloppe(ServicesEnveloppe services, VersionsDocuments versions, ChampsEnveloppe champs, ExigerValeurReferentiel referentiel) {
        this.services = services;
        this.versions = versions;
        this.champs = champs;
        this.referentiel = referentiel;
    }

    public VueEnveloppe envoyer(IdentifiantEnveloppe id) {
        Enveloppe e = services.trouver(id);
        List<VersionDocument> originales = versions.de(id).stream().filter(v -> ORIGINAL.equals(v.nature())).toList();
        boolean document = !originales.isEmpty();
        Enveloppe envoyee = e.envoyer(document, services.horloge().instant());
        if (document) {
            completerZonesSignature(e, originales.stream().mapToInt(VersionDocument::pages).max().orElse(1));
        }
        services.enveloppes().remplacer(envoyee);
        services.consigner(envoyee, "enveloppe-envoyee", Map.of());
        return VueEnveloppe.de(envoyee, versions.de(id));
    }

    /**
     * Chaque signataire sans zone de signature en reçoit une en bas de la dernière page (VS-070) : sa signature, dessinée
     * ou tapée, apparaît sur le document final. L'enveloppe préparée au portail, sans placement, en dépend.
     */
    private void completerZonesSignature(Enveloppe e, int dernierePage) {
        List<Champ> existants = champs.de(e.id());
        Set<UUID> avecZone = existants.stream().filter(c -> estZoneSignature(c.type())).map(Champ::signataire)
                .collect(Collectors.toSet());
        List<Signataire> manquants = ZonesSignatureParDefaut.sansZone(e.signataires(), avecZone);
        if (manquants.isEmpty()) {
            return;
        }
        ValeurReferentiel type = referentiel.executer(DefinirChamps.TYPES, ZONE_SIGNATURE);
        List<Champ> complets = new ArrayList<>(existants);
        for (int rang = 0; rang < manquants.size(); rang++) {
            Signataire s = manquants.get(rang);
            CodeLangue langue = new CodeLangue(s.langue());
            String nature = type.libelles().pour(langue, langue, type.code().valeur());
            String libelle = s.nom() == null || s.nom().isBlank() ? nature : (nature + " — " + s.nom().strip());
            libelle = libelle.length() > LIBELLE_MAX ? libelle.substring(0, LIBELLE_MAX) : libelle;
            complets.add(ZonesSignatureParDefaut.zone(services.identifiants().suivant(), s, rang, type.code().valeur(), libelle, dernierePage));
        }
        champs.definir(e.id(), complets);
        services.consigner(e, "zones-signature-par-defaut", Map.of("nombre", String.valueOf(manquants.size())));
    }

    /** Une zone d'initiales est remplie par l'acte de signer, mais ne montre pas la signature : elle n'en tient pas lieu. */
    private boolean estZoneSignature(String type) {
        Map<String, String> attributs = referentiel.executer(DefinirChamps.TYPES, new CodeReferentiel(type)).attributs();
        return "signature".equals(attributs.get("saisie")) && !"initiales".equals(attributs.get("rendu"));
    }

    /** Annulation : les dépôts encore ouverts ne servent plus (un dépôt n'accepte rien hors d'un brouillon). */
    public VueEnveloppe annuler(IdentifiantEnveloppe id) {
        Enveloppe annulee = services.trouver(id).annuler(services.horloge().instant());
        services.enveloppes().remplacer(annulee);
        services.consigner(annulee, "enveloppe-annulee", Map.of());
        return VueEnveloppe.de(annulee, versions.de(id));
    }

    public VueEnveloppe consulter(IdentifiantEnveloppe id) {
        Enveloppe e = services.trouver(id);
        return VueEnveloppe.de(e, versions.de(id)).avecEvenements(services.journal().de(id));
    }
}
