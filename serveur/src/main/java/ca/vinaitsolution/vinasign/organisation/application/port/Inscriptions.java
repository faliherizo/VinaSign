package ca.vinaitsolution.vinasign.organisation.application.port;

import ca.vinaitsolution.vinasign.organisation.domain.Inscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Demandes d'inscription (VS-122) : table de la plateforme, sans organisation. */
public interface Inscriptions {

    void ajouter(Inscription inscription);

    void remplacer(Inscription inscription);

    Optional<Inscription> parId(UUID id);

    Optional<Inscription> parEmpreinteJeton(String empreinte);

    /** Demande encore ouverte (ni acceptée ni refusée) pour ce courriel. */
    Optional<Inscription> ouverteDe(String courriel);

    /** Demandes en attente de validation, les plus anciennes d'abord. */
    List<Inscription> aValider();
}
