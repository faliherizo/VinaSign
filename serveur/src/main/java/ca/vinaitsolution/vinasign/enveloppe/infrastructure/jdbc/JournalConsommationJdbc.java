package ca.vinaitsolution.vinasign.enveloppe.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalConsommation;
import ca.vinaitsolution.vinasign.enveloppe.domain.Consommation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Journal des enveloppes, sous la RLS de l'organisation courante : seule la sienne est mesurée. */
@Component
class JournalConsommationJdbc implements JournalConsommation {

    private final NamedParameterJdbcTemplate jdbc;

    JournalConsommationJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Consommation.Evenement> entre(Instant debut, Instant fin) {
        return jdbc.query("SELECT enveloppe_id, type, survenu_le FROM evenement_enveloppe WHERE survenu_le >= :d AND survenu_le < :f",
                new MapSqlParameterSource("d", Timestamp.from(debut)).addValue("f", Timestamp.from(fin)),
                (rs, n) -> new Consommation.Evenement(rs.getObject("enveloppe_id", UUID.class), rs.getString("type"), rs.getTimestamp("survenu_le").toInstant()));
    }

    @Override
    public Set<UUID> envoyeesAvant(Instant debut) {
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT enveloppe_id FROM evenement_enveloppe WHERE type = :t AND survenu_le < :d",
                new MapSqlParameterSource("t", Consommation.ENVOI).addValue("d", Timestamp.from(debut)), UUID.class));
    }
}
