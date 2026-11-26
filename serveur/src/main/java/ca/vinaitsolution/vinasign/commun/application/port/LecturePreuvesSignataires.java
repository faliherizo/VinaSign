package ca.vinaitsolution.vinasign.commun.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ce que le contexte des sessions sait de chaque signataire d'une enveloppe, pour la preuve (VS-072, VS-073) : dernier
 * consentement (version et empreinte du texte), niveau d'authentification le plus récent atteint, méthodes attestées par
 * l'application. Lecture entre contextes : un DTO, jamais les entités.
 */
public interface LecturePreuvesSignataires {

    record PreuveSignataire(UUID signataire, String versionConsentement, String empreinteConsentement, Instant consentiLe, String niveau,
                            List<String> methodesAttestees) {

        public PreuveSignataire {
            methodesAttestees = methodesAttestees == null ? List.of() : List.copyOf(methodesAttestees);
        }
    }

    List<PreuveSignataire> de(UUID enveloppe);
}
