package ca.vinaitsolution.vinasign.enveloppe.domain;

import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier;
import java.util.List;

/** Champs obligatoires non remplis : le message les nomme, pour dire quoi corriger (VS-052). */
public class ChampsIncomplets extends ErreurMetier {

    private final List<String> libelles;

    public ChampsIncomplets(List<String> libelles) {
        super(Nature.INVALIDE, "champs-obligatoires", "Champs obligatoires à remplir : " + String.join(", ", libelles) + ".");
        this.libelles = List.copyOf(libelles);
    }

    public List<String> libelles() {
        return libelles;
    }
}
