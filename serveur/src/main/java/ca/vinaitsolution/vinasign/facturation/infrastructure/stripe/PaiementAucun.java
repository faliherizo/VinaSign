package ca.vinaitsolution.vinasign.facturation.infrastructure.stripe;

import ca.vinaitsolution.vinasign.facturation.application.port.PrestatairePaiement;
import ca.vinaitsolution.vinasign.facturation.domain.EvenementPaiement;
import ca.vinaitsolution.vinasign.facturation.domain.PaiementIndisponible;
import ca.vinaitsolution.vinasign.facturation.domain.PaiementRefuse;

/** Aucun prestataire configuré (défaut ; pilote interne facturé hors ligne) : rien ne se paie, aucun événement n'est accepté. */
public class PaiementAucun implements PrestatairePaiement {

    @Override
    public String code() {
        return "aucun";
    }

    @Override
    public String ouvrirPaiement(Souscription souscription) {
        throw new PaiementIndisponible("Le paiement en ligne n'est pas offert sur cette plateforme.");
    }

    @Override
    public String ouvrirGestion(String client) {
        throw new PaiementIndisponible("Le paiement en ligne n'est pas offert sur cette plateforme.");
    }

    @Override
    public EvenementPaiement lire(byte[] corps, String signature) {
        throw new PaiementRefuse("Aucun prestataire de paiement configuré.");
    }
}
