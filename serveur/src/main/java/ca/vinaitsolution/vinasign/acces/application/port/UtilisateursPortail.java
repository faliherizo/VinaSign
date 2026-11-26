package ca.vinaitsolution.vinasign.acces.application.port;

import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Utilisateurs du portail de l'organisation courante. */
public interface UtilisateursPortail {

    /** Ajoute un utilisateur de l'organisation courante ; {@code CourrielDejaInscrit} si le courriel est pris. */
    void ajouter(UtilisateurPortail utilisateur);

    /**
     * Ajoute le premier administrateur d'une organisation, par l'administration de la plateforme (qui n'agit au nom
     * d'aucune organisation) ; {@code CourrielDejaInscrit} si le courriel est pris.
     */
    void ajouterPourLaPlateforme(UtilisateurPortail utilisateur);

    Optional<UtilisateurPortail> parId(UUID id);

    List<UtilisateurPortail> deLOrganisationCourante();

    void remplacer(UtilisateurPortail utilisateur);
}
