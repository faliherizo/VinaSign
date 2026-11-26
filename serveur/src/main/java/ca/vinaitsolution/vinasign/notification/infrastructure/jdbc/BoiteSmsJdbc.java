package ca.vinaitsolution.vinasign.notification.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.application.port.Chiffrement;
import ca.vinaitsolution.vinasign.notification.application.port.BoiteSms;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Boîte d'envoi des SMS dans PostgreSQL : numéro chiffré, expédition par ses fonctions de plateforme. */
@Component
class BoiteSmsJdbc implements BoiteSms {

    private final NamedParameterJdbcTemplate jdbc;
    private final Chiffrement chiffrement;

    BoiteSmsJdbc(NamedParameterJdbcTemplate jdbc, Chiffrement chiffrement) {
        this.jdbc = jdbc;
        this.chiffrement = chiffrement;
    }

    @Override
    public void deposer(UUID id, UUID organisation, String numero, String texte, Instant prochaine) {
        jdbc.update("INSERT INTO sms_sortant (id, tenant_id, numero_chiffre, texte, prochaine_tentative) VALUES (:id, :org, :n, :t, :p)",
                new MapSqlParameterSource().addValue("id", id).addValue("org", organisation).addValue("n", chiffrement.chiffrer(numero))
                        .addValue("t", texte).addValue("p", Timestamp.from(prochaine)));
    }

    @Override
    public List<SmsDu> dus(Instant maintenant, int limite) {
        return jdbc.query("SELECT * FROM sms_dus(:m, :l)", new MapSqlParameterSource("m", Timestamp.from(maintenant)).addValue("l", limite),
                (rs, n) -> new SmsDu(rs.getObject("id", UUID.class), chiffrement.dechiffrer(rs.getBytes("numero_chiffre")), rs.getString("texte"),
                        rs.getInt("tentatives")));
    }

    @Override
    public void envoye(UUID id, Instant le) {
        jdbc.queryForList("SELECT sms_envoye(:id, :le)", new MapSqlParameterSource("id", id).addValue("le", Timestamp.from(le)));
    }

    @Override
    public void echec(UUID id, int tentatives, Instant prochaine, boolean abandonne) {
        jdbc.queryForList("SELECT sms_echec(:id, :t, :p, :a)", new MapSqlParameterSource("id", id).addValue("t", tentatives)
                .addValue("p", Timestamp.from(prochaine)).addValue("a", abandonne));
    }
}
