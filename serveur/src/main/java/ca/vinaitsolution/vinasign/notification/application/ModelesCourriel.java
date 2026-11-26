package ca.vinaitsolution.vinasign.notification.application;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.util.Map;

/**
 * Modèles de courriel (VS-080) : le référentiel {@code modele-courriel} porte, par modèle, le sujet et le corps dans
 * chaque langue (valeurs {@code <modele>-sujet} et {@code <modele>-corps}). Les variables {@code {nom}}, {@code {lien}}…
 * sont remplacées ; une langue ajoutée en base l'est sans redéploiement. Une organisation peut surcharger un modèle.
 */
public class ModelesCourriel {

    /** Sujet et corps rendus. */
    public record Rendu(String sujet, String corps) {
    }

    static final CodeReferentiel MODELES = new CodeReferentiel("modele-courriel");

    private final Referentiels referentiels;
    private final CodeLangue repli;

    public ModelesCourriel(Referentiels referentiels, CodeLangue repli) {
        this.referentiels = referentiels;
        this.repli = repli;
    }

    public Rendu rendre(String modele, String langue, Map<String, String> variables) {
        return new Rendu(texte(modele + "-sujet", langue, variables), texte(modele + "-corps", langue, variables));
    }

    private String texte(String code, String langue, Map<String, String> variables) {
        ValeurReferentiel v = referentiels.type(MODELES).flatMap(t -> t.valeurActive(new CodeReferentiel(code)))
                .orElseThrow(() -> new IllegalStateException("Modèle de courriel absent : " + code));
        String texte = v.libelles().pour(new CodeLangue(langue), repli, code);
        for (Map.Entry<String, String> e : variables.entrySet()) {
            texte = texte.replace("{" + e.getKey() + "}", e.getValue());
        }
        return texte;
    }
}
