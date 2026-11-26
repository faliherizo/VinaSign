package ca.vinaitsolution.vinasign.client;

import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Attestation d'un utilisateur déjà authentifié par l'application (VS-044) : à joindre à la demande de session intégrée
 * (champ {@code attestation}). Au niveau renforcé, {@code amr} doit contenir une seconde preuve (otp, hwk, mfa) datant de
 * moins de cinq minutes ({@code authentifieLe}).
 */
public final class Attestations {

    private final String emetteur;
    private final String clientId;
    private final PrivateKey cle;
    private final Clock horloge;

    public Attestations(String emetteur, String clientId, PrivateKey cle, Clock horloge) {
        this.emetteur = emetteur;
        this.clientId = clientId;
        this.cle = cle;
        this.horloge = horloge;
    }

    /** @param sujet identifiant de l'utilisateur chez l'application, déclaré comme sujet externe du signataire */
    public String attester(String sujet, List<String> methodes, Instant authentifieLe) {
        Instant t = horloge.instant();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("iss", clientId);
        r.put("sub", sujet);
        r.put("aud", emetteur + "/attestations");
        r.put("jti", UUID.randomUUID().toString());
        r.put("iat", t.getEpochSecond());
        r.put("exp", t.plusSeconds(60).getEpochSecond());
        r.put("amr", List.copyOf(methodes));
        r.put("auth_time", authentifieLe.getEpochSecond());
        return JwtSigne.signer(cle, null, r);
    }
}
