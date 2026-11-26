package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Quota mensuel d'enveloppes du plan atteint (VS-015). */
public class QuotaAtteint extends ErreurMetier {

    public QuotaAtteint() {
        super(Nature.INTERDIT, "quota-enveloppes-atteint", "Le quota mensuel d'enveloppes du plan est atteint.");
    }
}
