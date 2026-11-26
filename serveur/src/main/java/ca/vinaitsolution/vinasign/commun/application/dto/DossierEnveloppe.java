package ca.vinaitsolution.vinasign.commun.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enveloppe vue par un autre contexte (session, notification, preuve) : de quoi inviter et authentifier ses signataires,
 * sans exposer l'agrégat du contexte « enveloppe ».
 *
 * @param statut code du référentiel {@code statut-enveloppe}
 */
public record DossierEnveloppe(UUID id, UUID organisation, String application, String referenceExterne, String mode, String statut,
                               Instant expireLe, List<SignataireDossier> signataires) {

    /**
     * @param peutAgir    vrai si c'est son tour de signer
     * @param niveauExige niveau d'authentification à atteindre avant de signer, ou null
     */
    public record SignataireDossier(UUID id, String nom, String courriel, String langue, String type, int ordre, String statut,
                                    String sujetExterne, boolean peutAgir, String telephone, String niveauExige) {

        /** Sans niveau exigé. */
        public SignataireDossier(UUID id, String nom, String courriel, String langue, String type, int ordre, String statut, String sujetExterne,
                                 boolean peutAgir, String telephone) {
            this(id, nom, courriel, langue, type, ordre, statut, sujetExterne, peutAgir, telephone, null);
        }

        /** Sans le courriel ni le téléphone. */
        @Override
        public String toString() {
            return "SignataireDossier[" + id + ", ordre=" + ordre + ", " + statut + "]";
        }
    }

    public List<SignataireDossier> peuventAgir() {
        return signataires.stream().filter(SignataireDossier::peutAgir).toList();
    }
}
