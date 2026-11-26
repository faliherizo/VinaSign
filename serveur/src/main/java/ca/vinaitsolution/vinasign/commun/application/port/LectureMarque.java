package ca.vinaitsolution.vinasign.commun.application.port;

import java.util.Optional;
import java.util.UUID;

/** Marque d'une organisation telle que l'interface du signataire l'affiche (VS-123) ; lecture entre contextes. */
public interface LectureMarque {

    /** @param logo PNG en URL de données ({@code data:image/png;base64,…}), ou null */
    record VueMarque(String nomAffiche, String couleurPrimaire, String logo) {
    }

    Optional<VueMarque> deOrganisation(UUID organisation);
}
