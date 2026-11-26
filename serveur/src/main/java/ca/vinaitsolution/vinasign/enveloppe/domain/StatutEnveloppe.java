package ca.vinaitsolution.vinasign.enveloppe.domain;

/**
 * Machine d'états de l'enveloppe (VS-024), portée par {@link Enveloppe} ; libellés : référentiel {@code statut-enveloppe}.
 * brouillon → envoyée → complétée ; brouillon ou envoyée → annulée ; envoyée → expirée ou refusée.
 */
public enum StatutEnveloppe implements CodeDomaine {
    BROUILLON("brouillon"), ENVOYEE("envoyee"), COMPLETEE("terminee"), ANNULEE("annulee"), EXPIREE("expiree"), REFUSEE("refusee");

    private final String code;

    StatutEnveloppe(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean estTerminal() {
        return this != BROUILLON && this != ENVOYEE;
    }

    public static StatutEnveloppe depuisCode(String code) {
        return CodeDomaine.depuis(StatutEnveloppe.class, code);
    }
}
