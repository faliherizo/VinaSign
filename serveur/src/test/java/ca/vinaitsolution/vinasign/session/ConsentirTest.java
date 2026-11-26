package ca.vinaitsolution.vinasign.session;

import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import ca.vinaitsolution.vinasign.session.application.Consentir;
import ca.vinaitsolution.vinasign.session.application.ServicesSession;
import ca.vinaitsolution.vinasign.session.application.TexteConsentement;
import ca.vinaitsolution.vinasign.session.application.port.Consentements;
import ca.vinaitsolution.vinasign.session.domain.Consentement;
import ca.vinaitsolution.vinasign.session.domain.ConsentementRequis;
import ca.vinaitsolution.vinasign.session.domain.LienInvalide;
import ca.vinaitsolution.vinasign.session.domain.VersionConsentementPerimee;
import ca.vinaitsolution.vinasign.support.ReferentielsEnMemoire;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsentirTest {

    private static final Instant T0 = Instant.parse("2026-11-22T23:00:00Z");
    private final UUID enveloppe = UUID.randomUUID();
    private final UUID signataire = UUID.randomUUID();
    private final List<Consentement> stock = new ArrayList<>();
    private final Consentements consentements = new Consentements() {
        public void ajouter(Consentement c) {
            stock.add(c);
        }

        public Optional<Consentement> dernier(UUID s) {
            return stock.stream().filter(c -> c.signataire().equals(s)).max(Comparator.comparing(Consentement::accepteLe));
        }
    };
    private String enVigueur = "v2";

    private ReferentielsEnMemoire textes() {
        return new ReferentielsEnMemoire().avec(type("texte-consentement",
                new ValeurReferentiel(new CodeReferentiel("v1"), Libelles.de(Map.of("fr", "Ancien texte")), 1, Map.of(), true),
                new ValeurReferentiel(new CodeReferentiel("v2"), Libelles.de(Map.of("fr", "J'accepte de signer.", "en", "I agree to sign.")), 2,
                        "v2".equals(enVigueur) ? Map.of("enVigueur", "true") : Map.of(), true)));
    }

    private Consentir consentir() {
        DossierEnveloppe d = new DossierEnveloppe(enveloppe, UUID.randomUUID(), "app", "r", "empreinte", "envoyee", T0.plusSeconds(3600),
                List.of(new DossierEnveloppe.SignataireDossier(signataire, "Alice", "a@exemple.ca", "en", "invite", 1, "en-attente", null, true, null)));
        return new Consentir(consentements, id -> id.equals(enveloppe) ? Optional.of(d) : Optional.empty(), new TexteConsentement(textes(),
                new CodeLangue("fr")), new ServicesSession(UUID::randomUUID, new SecureRandom(), Clock.fixed(T0, ZoneOffset.UTC),
                new ServicesSession.Reglages(Duration.ofDays(1), Duration.ofMinutes(30), 5)));
    }

    @Test
    void sans_consentement_ou_avec_une_version_perimee_toute_signature_est_refusee() {
        Consentir c = consentir();
        assertThrows(ConsentementRequis.class, () -> c.exiger(enveloppe, signataire));
        assertThrows(VersionConsentementPerimee.class, () -> c.executer(enveloppe, signataire, "v1"));
        Consentement accepte = c.executer(enveloppe, signataire, "v2");
        assertEquals(ServicesSession.empreinte("I agree to sign."), accepte.empreinteTexte(), "texte affiché dans la langue du signataire");
        assertEquals(T0, accepte.accepteLe());
        c.exiger(enveloppe, signataire);
    }

    @Test
    void sans_texte_marque_en_vigueur_la_version_la_plus_recente_s_applique() {
        enVigueur = "aucune";
        assertEquals("v2", new TexteConsentement(textes(), new CodeLangue("fr")).enVigueur("fr").version());
        assertThrows(IllegalStateException.class, () -> new TexteConsentement(new ReferentielsEnMemoire(), new CodeLangue("fr")).enVigueur("fr"));
        assertThrows(IllegalStateException.class, () -> new TexteConsentement(new ReferentielsEnMemoire().avec(type("texte-consentement")),
                new CodeLangue("fr")).enVigueur("fr"));
    }

    @Test
    void un_signataire_etranger_a_l_enveloppe_n_a_rien_a_consentir() {
        assertThrows(LienInvalide.class, () -> consentir().executer(enveloppe, UUID.randomUUID(), "v2"));
        assertThrows(LienInvalide.class, () -> consentir().exiger(UUID.randomUUID(), signataire));
    }
}
