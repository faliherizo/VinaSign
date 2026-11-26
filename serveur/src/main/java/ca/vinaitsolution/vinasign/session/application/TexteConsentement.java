package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.util.Comparator;

/**
 * Texte du consentement en vigueur (VS-041) : la valeur active du référentiel {@code texte-consentement} marquée
 * {@code enVigueur}, sinon la plus récente (ordre le plus élevé) ; son libellé dans la langue du signataire est le texte.
 * Une nouvelle version s'ajoute en base, sans redéploiement.
 */
public class TexteConsentement {

    /** Version (code de la valeur) et texte affiché. */
    public record Texte(String version, String texte) {
    }

    static final CodeReferentiel TEXTES = new CodeReferentiel("texte-consentement");

    private final Referentiels referentiels;
    private final CodeLangue repli;

    public TexteConsentement(Referentiels referentiels, CodeLangue repli) {
        this.referentiels = referentiels;
        this.repli = repli;
    }

    public Texte enVigueur(String langue) {
        TypeReferentiel type = referentiels.type(TEXTES).orElseThrow(() -> new IllegalStateException("Référentiel texte-consentement absent."));
        ValeurReferentiel v = type.valeursActives().stream().filter(x -> "true".equals(x.attributs().get("enVigueur"))).findFirst()
                .or(() -> type.valeursActives().stream().max(Comparator.comparingInt(ValeurReferentiel::ordre)))
                .orElseThrow(() -> new IllegalStateException("Aucun texte de consentement actif."));
        return new Texte(v.code().valeur(), v.libelles().pour(new CodeLangue(langue), repli, v.code().valeur()));
    }
}
