package ca.vinaitsolution.vinasign.session.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.session.application.port.DefisCode;
import ca.vinaitsolution.vinasign.session.domain.DefiCode;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Défis de code dans PostgreSQL, sous la RLS de l'organisation du signataire. */
@Component
class DefisCodeJdbc implements DefisCode {

    private final NamedParameterJdbcTemplate jdbc;

    DefisCodeJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(DefiCode d) {
        jdbc.update("""
                INSERT INTO defi_code (id, tenant_id, session_id, canal, empreinte_code, expire_le, essais, essais_max)
                SELECT :id, tenant_id, id, :canal, :h, :expire, :essais, :max FROM session_signataire WHERE id = :session""",
                new MapSqlParameterSource().addValue("id", d.id()).addValue("session", d.session()).addValue("canal", d.canal())
                        .addValue("h", d.empreinteCode()).addValue("expire", Timestamp.from(d.expireLe())).addValue("essais", d.essais())
                        .addValue("max", d.essaisMax()));
    }

    @Override
    public void remplacer(DefiCode d) {
        jdbc.update("UPDATE defi_code SET essais = :e, valide_le = :v WHERE id = :id", new MapSqlParameterSource().addValue("id", d.id())
                .addValue("e", d.essais()).addValue("v", d.valideLe() == null ? null : Timestamp.from(d.valideLe())));
    }

    @Override
    public Optional<DefiCode> dernier(UUID session) {
        return jdbc.query("SELECT * FROM defi_code WHERE session_id = :s ORDER BY cree_le DESC LIMIT 1", new MapSqlParameterSource("s", session),
                (rs, n) -> new DefiCode(rs.getObject("id", UUID.class), session, rs.getString("canal"), rs.getString("empreinte_code"),
                        rs.getTimestamp("expire_le").toInstant(), rs.getInt("essais"), rs.getInt("essais_max"),
                        rs.getTimestamp("valide_le") == null ? null : rs.getTimestamp("valide_le").toInstant())).stream().findFirst();
    }

    @Override
    public void elever(UUID session, String niveau) {
        jdbc.update("UPDATE session_signataire SET niveau_atteint = :n WHERE id = :s", new MapSqlParameterSource("s", session).addValue("n", niveau));
    }
}
