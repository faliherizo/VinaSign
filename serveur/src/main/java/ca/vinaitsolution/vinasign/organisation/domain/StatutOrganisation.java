package ca.vinaitsolution.vinasign.organisation.domain;

/**
 * Invariant du cycle de vie d'une organisation (porté par {@link Organisation}) ; le libellé affiché vient du
 * référentiel {@code statut-organisation}, dont les codes sont ceux de {@link #code()}.
 */
public enum StatutOrganisation {
    ACTIVE("active"), SUSPENDUE("suspendue");

    private final String code;

    StatutOrganisation(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static StatutOrganisation depuisCode(String code) {
        for (StatutOrganisation s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Statut d'organisation inconnu.");
    }
}
