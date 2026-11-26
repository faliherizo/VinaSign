package ca.vinaitsolution.vinasign.commun.application.port;

import ca.vinaitsolution.vinasign.commun.domain.EvenementAudit;

/** Journal d'audit : ajout seul. */
public interface JournalAudit {

    void consigner(EvenementAudit evenement);
}
