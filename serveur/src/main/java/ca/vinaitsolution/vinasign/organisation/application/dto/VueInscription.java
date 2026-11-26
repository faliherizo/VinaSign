package ca.vinaitsolution.vinasign.organisation.application.dto;

import ca.vinaitsolution.vinasign.organisation.domain.Inscription;
import java.time.Instant;
import java.util.UUID;

/**
 * Demande d'inscription vue par son demandeur (porteur du lien) ou par l'administrateur de la plateforme : étape, et
 * l'enregistrement DNS à publier quand le domaine reste à prouver.
 */
public record VueInscription(UUID id, String nomOrganisation, String courriel, String domaine, String etape, String enregistrementDns,
                             String valeurDns, Instant creeLe) {

    public static VueInscription de(Inscription i) {
        return new VueInscription(i.id(), i.nomOrganisation(), i.courriel(), i.domaine(), i.etat().code(), i.nomEnregistrement(),
                i.domaine() == null ? null : i.defiDns(), i.creeLe());
    }
}
