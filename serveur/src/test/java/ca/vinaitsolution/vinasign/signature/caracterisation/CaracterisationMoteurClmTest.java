package ca.vinaitsolution.vinasign.signature.caracterisation;

import java.time.Clock;
import qc.levis.contrat.clm.infrastructure.CachetX509;

/** La suite contre le moteur du CLM, tel qu'il est dans le contrat (copie fidèle), avec le cachet et l'horodatage de l'ICP. */
class CaracterisationMoteurClmTest extends CaracterisationCachet {

    @Override
    protected CachetSousEssai cachet() throws Exception {
        CachetX509 clm = new CachetX509(BacsASable.magasinCachet().toString(), BacsASable.motDePasseCachet(), BacsASable.icp() + "/tsa",
                Clock.systemUTC());
        return new CachetSousEssai() {
            @Override
            public byte[] scellerPdf(byte[] pdf, String motif) {
                return clm.scellerPdf(pdf, motif);
            }

            @Override
            public byte[] signerDetache(byte[] contenu) {
                return clm.signerDetache(contenu);
            }

            @Override
            public String niveauPades() {
                return clm.niveauPades();
            }
        };
    }
}
