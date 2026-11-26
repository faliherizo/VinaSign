package ca.vinaitsolution.vinasign.enveloppe.domain;

/**
 * Mode de l'enveloppe (VS-ADR-005) : un document à signer, ou une empreinte sans contenu. Il décide de ce que l'envoi
 * exige ; libellés : référentiel {@code mode-enveloppe}.
 */
public enum ModeEnveloppe implements CodeDomaine {
    DOCUMENT("document"), EMPREINTE("empreinte");

    private final String code;

    ModeEnveloppe(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static ModeEnveloppe depuisCode(String code) {
        return CodeDomaine.depuis(ModeEnveloppe.class, code);
    }
}
