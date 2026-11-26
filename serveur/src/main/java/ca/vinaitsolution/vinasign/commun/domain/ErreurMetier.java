package ca.vinaitsolution.vinasign.commun.domain;

/**
 * Erreur métier d'un contexte. Sa nature décide du statut HTTP et son type du problème RFC 9457 : la traduction en
 * réponse est faite une fois pour toutes, sans que l'infrastructure web connaisse chaque contexte.
 */
public abstract class ErreurMetier extends RuntimeException {

    /** Nature de l'erreur, vue de l'appelant. */
    public enum Nature {
        INTROUVABLE, CONFLIT, INVALIDE, INTERDIT, PERIME
    }

    private final Nature nature;
    private final String type;

    protected ErreurMetier(Nature nature, String type, String message) {
        super(message);
        this.nature = nature;
        this.type = type;
    }

    public Nature nature() {
        return nature;
    }

    /** Identifiant stable du problème (dernier segment de l'URI du type RFC 9457). */
    public String type() {
        return type;
    }
}
