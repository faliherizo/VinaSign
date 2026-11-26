package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.application.port.PreconditionsSignature;
import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.session.application.port.ClesAcces;
import ca.vinaitsolution.vinasign.session.domain.AuthentificationRequise;
import ca.vinaitsolution.vinasign.session.domain.LienInvalide;

/**
 * Aucune signature avant le niveau d'authentification exigé du signataire (VS-042, VS-043) : le rang atteint par la session
 * (code, clé d'accès, attestation) doit valoir au moins celui exigé (attribut « rang » du référentiel niveau-authentification).
 */
public class NiveauPrealable implements PreconditionsSignature {

    static final CodeReferentiel NIVEAUX = new CodeReferentiel("niveau-authentification");

    private final LectureEnveloppes enveloppes;
    private final ClesAcces sessions;
    private final ExigerValeurReferentiel referentiel;

    public NiveauPrealable(LectureEnveloppes enveloppes, ClesAcces sessions, ExigerValeurReferentiel referentiel) {
        this.enveloppes = enveloppes;
        this.sessions = sessions;
        this.referentiel = referentiel;
    }

    @Override
    public void exiger(IdentiteSignataire s) {
        String exige = enveloppes.dossier(s.enveloppe()).flatMap(d -> d.signataires().stream().filter(x -> x.id().equals(s.signataire())).findFirst())
                .orElseThrow(LienInvalide::new).niveauExige();
        if (exige == null) {
            return;
        }
        int atteint = sessions.niveauSession(s.session()).map(this::rang).orElse(0);
        if (atteint < rang(exige)) {
            throw new AuthentificationRequise(exige);
        }
    }

    private int rang(String niveau) {
        return Integer.parseInt(referentiel.executer(NIVEAUX, new CodeReferentiel(niveau)).attributs().getOrDefault("rang", "1"));
    }
}
