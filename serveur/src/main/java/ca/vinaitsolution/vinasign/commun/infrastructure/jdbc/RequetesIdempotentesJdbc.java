package ca.vinaitsolution.vinasign.commun.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.domain.IssueReservation;
import ca.vinaitsolution.vinasign.commun.application.port.RequetesIdempotentes;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Clés d'idempotence dans PostgreSQL (organisation posée par la RLS). Dans une transaction, une réservation concurrente
 * de la même clé attend la fin de la première : elle voit alors la réponse mémorisée, ou la clé libérée si la première
 * a échoué.
 */
@Component
class RequetesIdempotentesJdbc implements RequetesIdempotentes {

    private final NamedParameterJdbcTemplate jdbc;

    RequetesIdempotentesJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Reservation reserver(String client, String cle, String empreinte, Instant maintenant, Instant expireLe) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("client", client).addValue("cle", cle).addValue("empreinte", empreinte)
                .addValue("maintenant", Timestamp.from(maintenant)).addValue("expire", Timestamp.from(expireLe));
        jdbc.update("DELETE FROM requete_idempotente WHERE client_id = :client AND cle = :cle AND expire_le <= :maintenant", p);
        int inseree = jdbc.update("""
                INSERT INTO requete_idempotente (tenant_id, client_id, cle, empreinte, expire_le)
                VALUES (NULLIF(current_setting('app.organisation', true), '')::uuid, :client, :cle, :empreinte, :expire)
                ON CONFLICT (client_id, cle) DO NOTHING""", p);
        if (inseree == 1) {
            return new Reservation(IssueReservation.NOUVELLE, 0, null);
        }
        // Ligne absente alors que l'insertion a échoué : l'autre requête vient de se terminer et d'expirer ; la traiter en cours.
        return jdbc.queryForList("SELECT empreinte, statut, reponse FROM requete_idempotente WHERE client_id = :client AND cle = :cle", p).stream()
                .findFirst().map(l -> issue(l, empreinte)).orElse(new Reservation(IssueReservation.EN_COURS, 0, null));
    }

    private static Reservation issue(Map<String, Object> l, String empreinte) {
        if (!empreinte.equals(l.get("empreinte"))) {
            return new Reservation(IssueReservation.AUTRE_CORPS, 0, null);
        }
        Object statut = l.get("statut");
        return statut == null ? new Reservation(IssueReservation.EN_COURS, 0, null)
                : new Reservation(IssueReservation.REJOUEE, ((Number) statut).intValue(), (String) l.get("reponse"));
    }

    @Override
    public void memoriser(String client, String cle, int statut, String reponse) {
        jdbc.update("UPDATE requete_idempotente SET statut = :statut, reponse = :reponse WHERE client_id = :client AND cle = :cle",
                new MapSqlParameterSource().addValue("client", client).addValue("cle", cle).addValue("statut", statut).addValue("reponse", reponse));
    }
}
