package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import java.time.Instant;
import java.util.List;

/**
 * Enveloppes envoyées dont l'échéance est atteinte, toutes organisations confondues : seule lecture de la tâche
 * d'expiration, qui n'agit au nom d'aucune organisation.
 */
public interface EcheancesEnveloppes {

    List<Enveloppe> echues(Instant maintenant, int limite);

    /** Enregistre l'expiration d'une enveloppe échue et son événement, hors de toute organisation courante. */
    void expirer(Enveloppe expiree, ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe evenement);
}
