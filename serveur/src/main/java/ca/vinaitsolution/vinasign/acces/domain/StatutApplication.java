package ca.vinaitsolution.vinasign.acces.domain;

/**
 * Invariant du cycle de vie d'une application cliente (porté par {@link ApplicationCliente#revoquer}) ; son libellé
 * affiché vient du référentiel {@code statut-application}.
 */
public enum StatutApplication {
    ACTIVE, REVOQUEE
}
