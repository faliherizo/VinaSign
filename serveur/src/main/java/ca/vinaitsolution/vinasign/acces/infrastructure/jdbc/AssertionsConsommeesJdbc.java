package ca.vinaitsolution.vinasign.acces.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.acces.application.port.AssertionsConsommees;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Registre anti-rejeu en base : la clé primaire sur le jti rend la consommation atomique, même entre instances. */
@Component
class AssertionsConsommeesJdbc implements AssertionsConsommees {

    private final NamedParameterJdbcTemplate jdbc;

    AssertionsConsommeesJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean consommer(String jti, IdentifiantClient client, Instant expireLe) {
        return jdbc.update("""
                INSERT INTO assertion_client_utilisee (jti, client_id, expire_le) VALUES (:jti, :client, :expire)
                ON CONFLICT (jti) DO NOTHING""",
                new MapSqlParameterSource("jti", jti).addValue("client", client.valeur()).addValue("expire", Timestamp.from(expireLe))) == 1;
    }

    @Override
    public int purger(Instant maintenant) {
        return jdbc.update("DELETE FROM assertion_client_utilisee WHERE expire_le < :maintenant",
                new MapSqlParameterSource("maintenant", Timestamp.from(maintenant)));
    }
}
