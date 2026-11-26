package ca.vinaitsolution.vinasign.enveloppe.domain;

/** États d'un signataire (VS-024) ; libellés : référentiel {@code statut-signataire}. */
public enum StatutSignataire implements CodeDomaine {
    EN_ATTENTE("en-attente"), SIGNE("signe"), REFUSE("refuse");

    private final String code;

    StatutSignataire(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static StatutSignataire depuisCode(String code) {
        return CodeDomaine.depuis(StatutSignataire.class, code);
    }
}
