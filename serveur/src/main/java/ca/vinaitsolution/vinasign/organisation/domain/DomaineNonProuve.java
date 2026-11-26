package ca.vinaitsolution.vinasign.organisation.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;

/** L'enregistrement TXT attendu n'est pas (encore) publié : le message dit lequel (VS-122). */
public class DomaineNonProuve extends ErreurMetier {

    public DomaineNonProuve(String nom, String valeur) {
        super(Nature.CONFLIT, "domaine-non-prouve", "Enregistrement TXT « " + nom + " » de valeur « " + valeur + " » introuvable ; "
                + "la propagation DNS peut prendre quelques minutes.");
    }
}
