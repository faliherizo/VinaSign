package ca.vinaitsolution.vinasign.commun.domain;

/** Issue de la réservation d'une clé d'idempotence (VS-020) : invariant du mécanisme, non une liste métier. */
public enum IssueReservation {
    NOUVELLE, REJOUEE, AUTRE_CORPS, EN_COURS
}
