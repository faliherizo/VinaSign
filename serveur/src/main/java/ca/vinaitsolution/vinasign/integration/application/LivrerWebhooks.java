package ca.vinaitsolution.vinasign.integration.application;

import ca.vinaitsolution.vinasign.commun.application.port.Chiffrement;
import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.integration.application.port.EnvoiWebhook;
import ca.vinaitsolution.vinasign.integration.application.port.GardeDestinations;
import ca.vinaitsolution.vinasign.integration.application.port.Livraisons;
import ca.vinaitsolution.vinasign.integration.domain.DestinationInvalide;
import ca.vinaitsolution.vinasign.integration.domain.Livraison;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Livraison des webhooks (VS-091), tâche de plateforme : la boîte d'envoi est répartie entre les destinations, puis chaque
 * livraison due est envoyée, signée (HMAC horodaté) et identifiée par l'événement (le destinataire déduplique). La garde
 * anti-SSRF est réappliquée à chaque envoi : une adresse qui désigne désormais un hôte interne est refusée. Livraison
 * « au moins une fois » : le corps ne porte que des identifiants ; le destinataire relit l'enveloppe par l'API.
 */
public class LivrerWebhooks {

    static final String ACTEUR = "plateforme:webhooks";

    /** Envoi, garde et secret. */
    public record Canal(EnvoiWebhook envoi, GardeDestinations garde, Chiffrement chiffrement) {
    }

    /** Horloge et nombre de livraisons tentées par passage. */
    public record Cadence(Clock horloge, int lot) {
    }

    private final Livraisons livraisons;
    private final Canal canal;
    private final ExecutionOrganisation execution;
    private final Clock horloge;
    private final int lot;

    public LivrerWebhooks(Livraisons livraisons, Canal canal, ExecutionOrganisation execution, Cadence cadence) {
        this.livraisons = livraisons;
        this.canal = canal;
        this.execution = execution;
        this.horloge = cadence.horloge();
        this.lot = cadence.lot();
    }

    /** Rend le nombre de livraisons réussies. */
    public int executer() {
        livraisons.repartir(horloge.instant());
        int reussies = 0;
        for (Livraisons.Due d : livraisons.dues(horloge.instant(), lot)) {
            Livraison suivante = tenter(d);
            execution.pour(new IdentifiantOrganisation(d.organisation()), ACTEUR, () -> {
                livraisons.remplacer(suivante);
                return null;
            });
            reussies += suivante.livreLe() == null ? 0 : 1;
        }
        return reussies;
    }

    private Livraison tenter(Livraisons.Due d) {
        Instant maintenant = horloge.instant();
        byte[] corps = corps(d);
        String secret = canal.chiffrement().dechiffrer(Base64.getDecoder().decode(d.secretChiffre()));
        Map<String, String> entetes = Map.of("Content-Type", "application/json; charset=utf-8",
                "VinaSign-Signature", SignatureWebhook.signer(secret.getBytes(StandardCharsets.UTF_8), maintenant.getEpochSecond(), corps),
                "VinaSign-Event-Id", d.livraison().evenement().toString(), "VinaSign-Event-Type", d.type());
        try {
            canal.garde().exiger(d.url());
            int code = canal.envoi().envoyer(d.url(), entetes, corps);
            return code >= 200 && code < 300 ? d.livraison().reussie(code, maintenant) : d.livraison().echouee(code, maintenant);
        } catch (EnvoiWebhook.Injoignable | DestinationInvalide e) {
            return d.livraison().echouee(null, maintenant);
        }
    }

    /** Identifiants et horodatage seulement : aucun renseignement personnel ne quitte la plateforme par un webhook. */
    static byte[] corps(Livraisons.Due d) {
        return ("{\"id\":\"" + d.livraison().evenement() + "\",\"type\":\"" + d.type() + "\",\"createdAt\":\"" + d.survenuLe()
                + "\",\"data\":{\"envelopeId\":\"" + d.enveloppe() + "\"}}").getBytes(StandardCharsets.UTF_8);
    }
}
