package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.enveloppe.application.dto.VueEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.util.List;
import java.util.Map;

/**
 * Signataires d'une enveloppe en brouillon (VS-023) : langue et type validés contre leurs référentiels ; un type dont
 * l'attribut {@code exigeSujetExterne} vaut {@code true} (utilisateur de l'application) exige le sujet externe, un type
 * {@code exigeCourriel} exige un courriel (invité, joint par courriel).
 */
public class DefinirSignataires {

    /** Signataire demandé. */
    /** @param niveau niveau d'authentification exigé avant de signer (référentiel niveau-authentification), facultatif */
    public record Demande(String nom, String courriel, String langue, String type, int ordre, String sujetExterne, String telephone, String niveau) {
    }

    static final CodeReferentiel LANGUES = new CodeReferentiel("langue");
    static final CodeReferentiel NIVEAUX = new CodeReferentiel("niveau-authentification");
    static final CodeReferentiel TYPES = new CodeReferentiel("type-signataire");

    private final ServicesEnveloppe services;
    private final ExigerValeurReferentiel referentiel;
    private final VersionsDocuments versions;

    public DefinirSignataires(ServicesEnveloppe services, ExigerValeurReferentiel referentiel, VersionsDocuments versions) {
        this.services = services;
        this.referentiel = referentiel;
        this.versions = versions;
    }

    public VueEnveloppe executer(IdentifiantEnveloppe id, List<Demande> demandes) {
        Enveloppe e = services.trouver(id);
        List<Signataire> signataires = demandes.stream().map(this::signataire).toList();
        Enveloppe suivante = e.definirSignataires(signataires, services.horloge().instant());
        services.enveloppes().remplacer(suivante);
        services.consigner(suivante, "signataires-definis", Map.of("nombre", String.valueOf(signataires.size())));
        return VueEnveloppe.de(suivante, versions.de(id));
    }

    private Signataire signataire(Demande d) {
        referentiel.executer(LANGUES, new CodeReferentiel(d.langue()));
        ValeurReferentiel type = referentiel.executer(TYPES, new CodeReferentiel(d.type()));
        Signataire s = Signataire.nouveau(services.identifiants().suivant(), d.nom(), d.courriel(), d.langue(), d.type(), d.ordre(),
                d.sujetExterne()).avecTelephone(d.telephone()).avecNiveauExige(d.niveau());
        if (d.niveau() != null) {
            referentiel.executer(NIVEAUX, new CodeReferentiel(d.niveau()));
        }
        if ("true".equals(type.attributs().get("exigeSujetExterne")) && s.sujetExterne() == null) {
            throw new SaisieInvalide("sujet-externe-requis", "Ce type de signataire exige le sujet externe (identifiant de l'utilisateur dans l'application).");
        }
        if ("true".equals(type.attributs().get("exigeCourriel")) && s.courriel() == null) {
            throw new SaisieInvalide("courriel-requis", "Ce type de signataire exige un courriel.");
        }
        return s;
    }
}
