package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** Une clé publique sert à une seule application : ni partage entre organisations, ni entre bac à sable et production. */
public class CleDejaEnregistree extends ErreurMetier {

    public CleDejaEnregistree() {
        super(Nature.CONFLIT, "cle-deja-enregistree", "Cette clé publique est déjà enregistrée pour une autre application.");
    }
}
