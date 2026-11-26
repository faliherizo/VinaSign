package ca.vinaitsolution.vinasign.enveloppe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZonesSignatureParDefautTest {

    private static Signataire signataire(int ordre) {
        return Signataire.nouveau(UUID.randomUUID(), "S" + ordre, "s" + ordre + "@exemple.ca", "fr", "invite", ordre, null);
    }

    @Test
    void seulsLesSignatairesSansZoneEnRecoiventUneDansLeurOrdre() {
        Signataire premier = signataire(1), deuxieme = signataire(2), troisieme = signataire(3);
        assertThat(ZonesSignatureParDefaut.sansZone(List.of(troisieme, premier, deuxieme), Set.of(deuxieme.id())))
                .containsExactly(premier, troisieme);
    }

    @Test
    void troisZonesParRangeeEnBasDeLaDernierePageSansDebordement() {
        Signataire s = signataire(1);
        Champ premier = ZonesSignatureParDefaut.zone(UUID.randomUUID(), s, 0, "signature", "Signature — S1", 4);
        Champ troisieme = ZonesSignatureParDefaut.zone(UUID.randomUUID(), s, 2, "signature", "Signature — S1", 4);
        Champ quatrieme = ZonesSignatureParDefaut.zone(UUID.randomUUID(), s, 3, "signature", "Signature — S1", 4);
        assertThat(premier.page()).isEqualTo(4);
        assertThat(premier.signataire()).isEqualTo(s.id());
        assertThat(premier.obligatoire()).isTrue();
        assertThat(premier.x()).isEqualTo(0.05);
        assertThat(premier.y()).isEqualTo(0.88);
        assertThat(troisieme.x() + troisieme.largeur()).isLessThanOrEqualTo(1.0);
        assertThat(troisieme.y()).isEqualTo(premier.y());
        assertThat(quatrieme.x()).isEqualTo(premier.x());
        assertThat(quatrieme.y()).isLessThan(premier.y());
        assertThat(quatrieme.y() + quatrieme.hauteur()).isLessThan(premier.y());
    }

    @Test
    void auDelaDeLaPageLesRangeesRestentDansLaPage() {
        Champ lointain = ZonesSignatureParDefaut.zone(UUID.randomUUID(), signataire(1), 60, "signature", "Signature", 1);
        assertThat(lointain.y()).isZero();
    }
}
