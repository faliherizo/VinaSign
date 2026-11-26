package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.Ancrage;
import ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Journal de preuve (VS-071) : maillons d'une enveloppe, agrégats et ancrages de la plateforme. */
public interface JournalPreuve {

    /** Maillons de l'enveloppe, dans l'ordre des rangs, avec l'heure de consignation du dernier. */
    List<ChaineScellee.Maillon> maillons(IdentifiantEnveloppe enveloppe);

    Optional<Instant> derniereConsignation(IdentifiantEnveloppe enveloppe);

    record Agregat(int nombre, String empreinte) {
    }

    /** Empreinte agrégée, recalculée par la base, des événements consignés dans ]depuis, jusqua]. */
    Agregat agreger(Instant depuis, Instant jusqua);

    Optional<Ancrage> dernierAncrage();

    /** Ancrage dont la fenêtre ]depuis, jusqua] contient cette heure de consignation. */
    Optional<Ancrage> couvrant(Instant consigneLe);

    void ajouter(Ancrage ancrage);
}
