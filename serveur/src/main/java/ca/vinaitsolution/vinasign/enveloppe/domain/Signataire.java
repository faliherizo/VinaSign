package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Signataire d'une enveloppe (VS-023). {@code langue} et {@code type} sont des codes de référentiel ({@code langue},
 * {@code type-signataire}) ; {@code ordre} : les signataires de même ordre signent en parallèle, les ordres se suivent.
 * {@code sujetExterne} : identifiant de l'utilisateur dans l'application cliente, exigé par certains types.
 * {@code niveauExige} : niveau d'authentification à atteindre avant de signer (référentiel {@code niveau-authentification}), ou null.
 */
public record Signataire(UUID id, String nom, String courriel, String langue, String type, int ordre, String sujetExterne,
                         StatutSignataire statut, Instant agiLe, String telephone, String niveauExige) {

    private static final java.util.regex.Pattern E164 = java.util.regex.Pattern.compile("\\+[1-9]\\d{7,14}");

    /** Sans téléphone. */
    public Signataire(UUID id, String nom, String courriel, String langue, String type, int ordre, String sujetExterne, StatutSignataire statut,
                      Instant agiLe) {
        this(id, nom, courriel, langue, type, ordre, sujetExterne, statut, agiLe, null, null);
    }

    /** Sans niveau exigé. */
    public Signataire(UUID id, String nom, String courriel, String langue, String type, int ordre, String sujetExterne, StatutSignataire statut,
                      Instant agiLe, String telephone) {
        this(id, nom, courriel, langue, type, ordre, sujetExterne, statut, agiLe, telephone, null);
    }

    public Signataire {
        if (id == null || statut == null || langue == null || langue.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("Signataire incomplet.");
        }
        nom = nom == null ? "" : nom.strip();
        if (nom.isEmpty() || nom.length() > 200) {
            throw new IllegalArgumentException("Le nom du signataire est requis (200 caractères au plus).");
        }
        if (courriel != null && (courriel.length() > 320 || !courriel.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s.]{2,}"))) {
            throw new IllegalArgumentException("Courriel du signataire invalide.");
        }
        if (ordre < 1 || ordre > 100) {
            throw new IllegalArgumentException("L'ordre de signature va de 1 à 100.");
        }
        sujetExterne = sujetExterne == null || sujetExterne.isBlank() ? null : sujetExterne.strip();
        telephone = telephone == null || telephone.isBlank() ? null : telephone.replaceAll("[\\s().-]", "");
        niveauExige = niveauExige == null || niveauExige.isBlank() ? null : niveauExige;
        if (telephone != null && !E164.matcher(telephone).matches()) {
            throw new IllegalArgumentException("Téléphone du signataire invalide : format international +1XXXXXXXXXX.");
        }
    }

    public static Signataire nouveau(UUID id, String nom, String courriel, String langue, String type, int ordre, String sujetExterne) {
        return new Signataire(id, nom, courriel, langue, type, ordre, sujetExterne, StatutSignataire.EN_ATTENTE, null, null, null);
    }

    public Signataire avecTelephone(String numero) {
        return new Signataire(id, nom, courriel, langue, type, ordre, sujetExterne, statut, agiLe, numero, niveauExige);
    }

    public Signataire avecNiveauExige(String niveau) {
        return new Signataire(id, nom, courriel, langue, type, ordre, sujetExterne, statut, agiLe, telephone, niveau);
    }

    Signataire avecStatut(StatutSignataire nouveau, Instant le) {
        return new Signataire(id, nom, courriel, langue, type, ordre, sujetExterne, nouveau, le, telephone, niveauExige);
    }

    /** Sans le courriel ni le téléphone : un {@code toString} ne doit pas répandre un renseignement personnel. */
    @Override
    public String toString() {
        return "Signataire[" + id + ", ordre=" + ordre + ", " + statut.code() + "]";
    }
}
