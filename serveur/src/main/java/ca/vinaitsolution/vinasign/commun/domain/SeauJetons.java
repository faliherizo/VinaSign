package ca.vinaitsolution.vinasign.commun.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Seau à jetons (VS-015) : une capacité de {@code capacite} requêtes, rechargée régulièrement sur une minute. Une
 * requête consomme un jeton ; sans jeton, elle est refusée avec le délai d'attente du prochain. Immuable : chaque
 * tentative rend l'état suivant.
 */
public record SeauJetons(int capacite, double jetons, Instant derniereRecharge) {

    private static final double NANOS_PAR_MINUTE = 60_000_000_000d;

    /** Issue d'une tentative : l'état suivant du seau, et le délai à respecter si la requête est refusée. */
    public record Decision(SeauJetons seau, boolean acceptee, Duration attente) {
    }

    public SeauJetons {
        if (capacite < 1 || derniereRecharge == null) {
            throw new IllegalArgumentException("Un seau a une capacité positive et une date de recharge.");
        }
    }

    public static SeauJetons plein(int capacite, Instant maintenant) {
        return new SeauJetons(capacite, capacite, maintenant);
    }

    public Decision tenter(Instant maintenant) {
        long ecoule = Math.max(0, Duration.between(derniereRecharge, maintenant).toNanos());
        double disponibles = Math.min(capacite, jetons + ecoule * capacite / NANOS_PAR_MINUTE);
        Instant recharge = maintenant.isAfter(derniereRecharge) ? maintenant : derniereRecharge;
        if (disponibles >= 1) {
            return new Decision(new SeauJetons(capacite, disponibles - 1, recharge), true, Duration.ZERO);
        }
        long attente = (long) Math.ceil((1 - disponibles) * NANOS_PAR_MINUTE / capacite);
        return new Decision(new SeauJetons(capacite, disponibles, recharge), false, Duration.ofNanos(attente));
    }

    /** Vrai si le seau est plein depuis cet instant : il peut être oublié sans rien changer aux décisions. */
    public boolean estPleinA(Instant maintenant) {
        return jetons + Math.max(0, Duration.between(derniereRecharge, maintenant).toNanos()) * capacite / NANOS_PAR_MINUTE >= capacite;
    }
}
