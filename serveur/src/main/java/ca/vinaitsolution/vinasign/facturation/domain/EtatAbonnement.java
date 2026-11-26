package ca.vinaitsolution.vinasign.facturation.domain;

/** État d'un abonnement, traduit du vocabulaire du prestataire : seul un abonnement actif donne droit à son plan. */
public enum EtatAbonnement {
    ACTIF("actif"), IMPAYE("impaye"), TERMINE("termine");

    private final String code;

    EtatAbonnement(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static EtatAbonnement deCode(String code) {
        for (EtatAbonnement e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("État d'abonnement inconnu : " + code);
    }
}
