package ca.vinaitsolution.vinasign.acces.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.acces.application.port.BacsASable;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Par la fonction {@code bac_a_sable_reinitialiser}, seule autorisée à changer l'organisation d'une application. */
@Component
class BacsASableJdbc implements BacsASable {

    private final NamedParameterJdbcTemplate jdbc;

    BacsASableJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> reinitialiser(IdentifiantClient client, UUID nouvelle, Instant le) {
        return Optional.ofNullable(jdbc.queryForObject("SELECT bac_a_sable_reinitialiser(:c, :n, :le)",
                new MapSqlParameterSource("c", client.valeur()).addValue("n", nouvelle).addValue("le", Timestamp.from(le)), UUID.class));
    }
}
