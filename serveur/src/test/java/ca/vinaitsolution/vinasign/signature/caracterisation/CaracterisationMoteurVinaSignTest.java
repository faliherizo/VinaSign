package ca.vinaitsolution.vinasign.signature.caracterisation;

import ca.vinaitsolution.vinasign.signature.infrastructure.cle.ClePkcs12;
import ca.vinaitsolution.vinasign.signature.infrastructure.pades.CachetPades;
import java.time.Clock;

/**
 * La même suite contre le moteur porté dans VinaSign (VS-032), avec le même cachet et la même autorité d'horodatage de
 * l'ICP : au vert pour les deux moteurs, la substitution est démontrée (VS-ADR-002).
 */
class CaracterisationMoteurVinaSignTest extends CaracterisationCachet {

    @Override
    protected CachetSousEssai cachet() throws Exception {
        CachetPades vinasign = new CachetPades(new ClePkcs12(BacsASable.magasinCachet(), BacsASable.motDePasseCachet().toCharArray()),
                BacsASable.icp() + "/tsa", Clock.systemUTC());
        return new CachetSousEssai() {
            @Override
            public byte[] scellerPdf(byte[] pdf, String motif) {
                return vinasign.scellerPdf(pdf, motif);
            }

            @Override
            public byte[] signerDetache(byte[] contenu) {
                return vinasign.signerDetache(contenu);
            }

            @Override
            public String niveauPades() {
                return vinasign.niveauPades();
            }
        };
    }
}
