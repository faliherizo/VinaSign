package ca.vinaitsolution.vinasign.notification.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.notification.application.port.BoiteEnvoi;
import ca.vinaitsolution.vinasign.notification.domain.CourrielSortant;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Boîte d'envoi dans PostgreSQL : dépôt sous la RLS, expédition par ses fonctions de plateforme. */
@Component
class BoiteEnvoiJdbc implements BoiteEnvoi {

    private final NamedParameterJdbcTemplate jdbc;

    BoiteEnvoiJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void deposer(CourrielSortant c) {
        jdbc.update("""
                INSERT INTO courriel_sortant (id, tenant_id, destinataire, sujet, corps, prochaine_tentative, enveloppe_id)
                VALUES (:id, :org, :dest, :sujet, :corps, :prochaine, :enveloppe)""", new MapSqlParameterSource().addValue("id", c.id())
                .addValue("enveloppe", c.enveloppe())
                .addValue("org", c.organisation()).addValue("dest", c.destinataire()).addValue("sujet", c.sujet()).addValue("corps", c.corps())
                .addValue("prochaine", Timestamp.from(c.prochaineTentative())));
    }

    @Override
    public List<CourrielSortant> dus(Instant maintenant, int limite) {
        return jdbc.query("SELECT * FROM courriel_dus(:m, :l)", new MapSqlParameterSource("m", Timestamp.from(maintenant)).addValue("l", limite),
                (rs, n) -> new CourrielSortant(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("destinataire"),
                        rs.getString("sujet"), rs.getString("corps"), rs.getInt("tentatives"), rs.getTimestamp("prochaine_tentative").toInstant()));
    }

    @Override
    public void envoye(UUID id, Instant le) {
        jdbc.queryForList("SELECT courriel_envoye(:id, :le)", new MapSqlParameterSource("id", id).addValue("le", Timestamp.from(le)));
    }

    @Override
    public void echec(UUID id, int tentatives, Instant prochaine, boolean abandonne) {
        jdbc.queryForList("SELECT courriel_echec(:id, :t, :p, :a)", new MapSqlParameterSource("id", id).addValue("t", tentatives)
                .addValue("p", Timestamp.from(prochaine)).addValue("a", abandonne));
    }
}
