package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.RequetesIdempotentes;
import ca.vinaitsolution.vinasign.commun.domain.ErreurMetier.Nature;
import ca.vinaitsolution.vinasign.commun.domain.RequeteIdempotenteRefusee;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Exécution idempotente d'une requête (VS-020), dans la transaction de l'appelant : même clé et même corps rendent la
 * même réponse sans rejouer l'action ; même clé et autre corps, 422 ; la même requête encore en cours, 409. Seule une
 * réponse réussie est mémorisée : une erreur annule la transaction, la clé avec elle.
 */
@Component
public class Idempotence {

    public static final String ENTETE = "Idempotency-Key";
    static final String REJOUEE = "Idempotent-Replayed";
    private static final Pattern CLE = Pattern.compile("[\\x21-\\x7e]{1,200}");
    private static final Duration CONSERVATION = Duration.ofHours(24);

    private final RequetesIdempotentes requetes;
    private final ContexteAccesCourant acces;
    private final ObjectMapper canonique;
    private final ObjectMapper json;
    private final Clock horloge;

    public Idempotence(RequetesIdempotentes requetes, ContexteAccesCourant acces, ObjectMapper json, Clock horloge) {
        this.requetes = requetes;
        this.acces = acces;
        this.json = json;
        this.canonique = json.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.horloge = horloge;
    }

    public ResponseEntity<?> executer(String cle, Object corps, Supplier<ResponseEntity<?>> action) {
        if (cle == null || !CLE.matcher(cle).matches()) {
            throw new IllegalArgumentException("En-tête Idempotency-Key requis : 1 à 200 caractères imprimables.");
        }
        String client = acces.courant().client().orElseThrow(() -> new IllegalArgumentException("Requête idempotente sans application."));
        Instant maintenant = horloge.instant();
        RequetesIdempotentes.Reservation r = requetes.reserver(client, cle, empreinte(corps), maintenant, maintenant.plus(CONSERVATION));
        switch (r.etat()) {
            case REJOUEE:
                return ResponseEntity.status(r.statut()).contentType(MediaType.APPLICATION_JSON).header(REJOUEE, "true").body(r.reponse());
            case AUTRE_CORPS:
                throw new RequeteIdempotenteRefusee(Nature.INVALIDE, "cle-idempotence-reutilisee",
                        "Cette clé d'idempotence a déjà servi pour une requête différente.");
            case EN_COURS:
                throw new RequeteIdempotenteRefusee(Nature.CONFLIT, "requete-en-cours", "Une requête portant cette clé est en cours.");
            default:
                ResponseEntity<?> reponse = action.get();
                requetes.memoriser(client, cle, reponse.getStatusCode().value(), ecrire(json, reponse.getBody()));
                return reponse;
        }
    }

    private String empreinte(Object corps) {
        return ca.vinaitsolution.vinasign.commun.domain.Condensats.sha256Hex(ecrire(canonique, corps));
    }

    private static String ecrire(ObjectMapper m, Object valeur) {
        return ca.vinaitsolution.vinasign.commun.infrastructure.Json.ecrire(m, valeur, "Corps de la requête");
    }
}
