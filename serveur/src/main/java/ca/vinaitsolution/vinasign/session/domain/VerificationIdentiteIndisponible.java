package ca.vinaitsolution.vinasign.session.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Aucun fournisseur de vérification d'identité n'est configuré pour cette plateforme (VS-131). */
public class VerificationIdentiteIndisponible extends ErreurMetier {

    public VerificationIdentiteIndisponible() {
        super(Nature.INVALIDE, "verification-identite-indisponible",
                "La vérification d'identité n'est pas offerte sur cette plateforme ; s'authentifier par code ou clé d'accès.");
    }
}
