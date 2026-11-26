package ca.vinaitsolution.vinasign.organisation.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Demande d'inscription en libre-service (VS-122). Elle avance par étapes, selon la politique de la plateforme : courriel
 * confirmé ; domaine prouvé (enregistrement DNS) ; validation par l'administrateur de la plateforme ; puis acceptée, elle
 * devient une organisation du même identifiant. Le courriel du demandeur appartient au domaine à prouver.
 */
public record Inscription(UUID id, String nomOrganisation, String courriel, String domaine, String langue, Etat etat, String empreinteJeton,
                          Instant jetonExpireLe, String defiDns, String motifRefus, Instant creeLe, Instant modifieLe) {

    private static final Pattern COURRIEL = Pattern.compile("[^@\\s]{1,64}@([a-z0-9-]+(\\.[a-z0-9-]+)+)");
    private static final Pattern DOMAINE = Pattern.compile("(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}");

    /** Étapes de la demande : machine d'états du domaine. */
    public enum Etat {
        COURRIEL_A_CONFIRMER("courriel-a-confirmer"), DOMAINE_A_PROUVER("domaine-a-prouver"), A_VALIDER("a-valider"), ACCEPTEE("acceptee"),
        REFUSEE("refusee");

        private final String code;

        Etat(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Etat deCode(String code) {
            for (Etat e : values()) {
                if (e.code.equals(code)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Étape d'inscription inconnue : " + code);
        }
    }

    /** Ce que la plateforme exige avant d'accepter : preuve du domaine, validation par l'administrateur de la plateforme. */
    public record Politique(boolean exigeDomaine, boolean exigeValidation) {
    }

    public Inscription {
        if (id == null || etat == null || empreinteJeton == null || jetonExpireLe == null || defiDns == null || creeLe == null || modifieLe == null) {
            throw new IllegalArgumentException("Inscription incomplète.");
        }
    }

    /** Nouvelle demande : nom, courriel valide et, si la politique l'exige, domaine dont relève le courriel. */
    public static Inscription demander(UUID id, Demande d, Politique politique, Instant le) {
        String nom = d.nomOrganisation() == null ? "" : d.nomOrganisation().strip();
        if (nom.isEmpty() || nom.length() > 200) {
            throw new InscriptionInvalide("Le nom de l'organisation est requis (200 caractères au plus).");
        }
        String courriel = d.courriel() == null ? "" : d.courriel().strip().toLowerCase(Locale.ROOT);
        var m = COURRIEL.matcher(courriel);
        if (courriel.length() > 320 || !m.matches()) {
            throw new InscriptionInvalide("Adresse courriel invalide.");
        }
        String domaine = d.domaine() == null || d.domaine().isBlank() ? null : d.domaine().strip().toLowerCase(Locale.ROOT);
        if (politique.exigeDomaine() && domaine == null) {
            throw new InscriptionInvalide("Le domaine de l'organisation est requis.");
        }
        if (domaine != null && (!DOMAINE.matcher(domaine).matches() || !appartient(m.group(1), domaine))) {
            throw new InscriptionInvalide("Le courriel du demandeur doit appartenir au domaine de l'organisation.");
        }
        return new Inscription(id, nom, courriel, domaine, d.langue(), Etat.COURRIEL_A_CONFIRMER, d.empreinteJeton(), d.jetonExpireLe(), d.defiDns(),
                null, le, le);
    }

    /** Saisie du demandeur et secrets tirés par l'application. */
    public record Demande(String nomOrganisation, String courriel, String domaine, String langue, String empreinteJeton, Instant jetonExpireLe,
                          String defiDns) {
    }

    private static boolean appartient(String domaineCourriel, String domaine) {
        return domaineCourriel.equals(domaine) || domaineCourriel.endsWith("." + domaine);
    }

    /** Lien du courriel ouvert à temps : étape suivante selon la politique. */
    public Inscription confirmerCourriel(Politique politique, Instant le) {
        exiger(Etat.COURRIEL_A_CONFIRMER);
        if (!le.isBefore(jetonExpireLe)) {
            throw new InscriptionInvalide("Le lien de confirmation a expiré : refaire la demande.");
        }
        return vers(politique.exigeDomaine() ? Etat.DOMAINE_A_PROUVER : apresDomaine(politique), le);
    }

    /** Enregistrement TXT trouvé (ou non) sous le domaine. */
    public Inscription prouverDomaine(boolean trouve, Politique politique, Instant le) {
        exiger(Etat.DOMAINE_A_PROUVER);
        if (!trouve) {
            throw new DomaineNonProuve(nomEnregistrement(), defiDns);
        }
        return vers(apresDomaine(politique), le);
    }

    public Inscription valider(Instant le) {
        exiger(Etat.A_VALIDER);
        return vers(Etat.ACCEPTEE, le);
    }

    public Inscription refuser(String motif, Instant le) {
        if (etat == Etat.ACCEPTEE || etat == Etat.REFUSEE) {
            throw new InscriptionInvalide("Cette demande est déjà " + etat.code() + ".");
        }
        String m = motif == null ? "" : motif.strip();
        return new Inscription(id, nomOrganisation, courriel, domaine, langue, Etat.REFUSEE, empreinteJeton, jetonExpireLe, defiDns,
                m.isEmpty() ? null : m.substring(0, Math.min(m.length(), 500)), creeLe, le);
    }

    /** Nom DNS où publier l'enregistrement TXT de preuve. */
    public String nomEnregistrement() {
        return domaine == null ? null : "_vinasign-verification." + domaine;
    }

    public boolean estAcceptee() {
        return etat == Etat.ACCEPTEE;
    }

    private static Etat apresDomaine(Politique politique) {
        return politique.exigeValidation() ? Etat.A_VALIDER : Etat.ACCEPTEE;
    }

    private void exiger(Etat attendu) {
        if (etat != attendu) {
            throw new InscriptionInvalide("Étape attendue : " + attendu.code() + " ; la demande est à l'étape " + etat.code() + ".");
        }
    }

    private Inscription vers(Etat suivant, Instant le) {
        return new Inscription(id, nomOrganisation, courriel, domaine, langue, suivant, empreinteJeton, jetonExpireLe, defiDns, motifRefus, creeLe, le);
    }
}
