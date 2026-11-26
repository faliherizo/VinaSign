package ca.vinaitsolution.vinasign.referentiel.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.infrastructure.VinaSignProprietes;
import ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.ListerReferentiels;
import ca.vinaitsolution.vinasign.referentiel.application.dto.ResumeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.application.dto.VueReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Référentiels en lecture : source unique de toute liste de l'interface (listes déroulantes, boutons radio, cases).
 * Lecture publique : des libellés non sensibles, nécessaires à l'écran du signataire avant toute authentification
 * (motivé dans securite/donnees/surface-publique.json).
 */
@RestController
@RequestMapping("/api/v1/referentiels")
public class ReferentielController {

    private final ListerReferentiels lister;
    private final ConsulterReferentiel consulter;
    private final VinaSignProprietes proprietes;

    public ReferentielController(ListerReferentiels lister, ConsulterReferentiel consulter, VinaSignProprietes proprietes) {
        this.lister = lister;
        this.consulter = consulter;
        this.proprietes = proprietes;
    }

    @GetMapping
    public List<ResumeReferentiel> lister(@RequestParam(required = false) String langue,
                                          @RequestHeader(value = "Accept-Language", required = false) Locale locale) {
        return lister.executer(langue(langue, locale));
    }

    @GetMapping("/{code}")
    public VueReferentiel consulter(@PathVariable String code, @RequestParam(required = false) String langue,
                                    @RequestHeader(value = "Accept-Language", required = false) Locale locale) {
        return consulter.executer(new CodeReferentiel(code), langue(langue, locale));
    }

    /** Le paramètre explicite prime sur l'en-tête du navigateur, qui prime sur la langue principale configurée. */
    private CodeLangue langue(String parametre, Locale locale) {
        if (parametre != null && !parametre.isBlank()) {
            return new CodeLangue(parametre);
        }
        return locale != null && !locale.getLanguage().isBlank()
                ? new CodeLangue(locale.getLanguage()) : new CodeLangue(proprietes.languePrincipale());
    }
}
