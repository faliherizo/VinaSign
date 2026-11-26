package ca.vinaitsolution.vinasign.session.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Ce que l'interface du signataire reçoit à l'ouverture : sa session, son enveloppe, s'il peut agir, et le consentement
 * à accepter (version et texte dans sa langue), et les actions permises (vide : toutes, lien d'invitation). Pour une
 * session intégrée, les origines déclarées par l'application : seules destinataires des avis {@code postMessage} (VS-055). La marque de l'organisation (VS-123), si elle en a défini une.
 */
public record VueSession(String jetonSession, Instant expireLe, UUID enveloppe, String mode, String statutEnveloppe, String nom,
                         String langue, boolean peutAgir, boolean aConsenti, String versionConsentement, String texteConsentement,
                         java.util.List<String> actions, java.util.List<String> originesIntegration, String niveauExige, String niveauAtteint,
                         ca.vinaitsolution.vinasign.commun.application.port.LectureMarque.VueMarque marque) {
}
