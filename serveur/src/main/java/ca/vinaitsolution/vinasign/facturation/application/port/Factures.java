package ca.vinaitsolution.vinasign.facturation.application.port;

import ca.vinaitsolution.vinasign.facturation.domain.Facture;
import java.util.List;
import java.util.UUID;

/** Factures payées, sous la RLS (VS-121) ; une facture déjà connue n'est pas ajoutée deux fois. */
public interface Factures {

    void ajouter(Facture facture);

    List<Facture> deLOrganisation(UUID organisation);
}
