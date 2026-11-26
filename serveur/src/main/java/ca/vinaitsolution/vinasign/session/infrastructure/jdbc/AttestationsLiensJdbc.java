package ca.vinaitsolution.vinasign.session.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.session.application.port.AttestationsLiens;
import ca.vinaitsolution.vinasign.session.domain.AttestationRetenue;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Attestations dans PostgreSQL, sous la RLS de l'organisation (l'organisation est celle du lien). */
@Component
class AttestationsLiensJdbc implements AttestationsLiens {

    private final NamedParameterJdbcTemplate jdbc;

    AttestationsLiensJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(AttestationRetenue a) {
        jdbc.update("""
                INSERT INTO attestation_lien (lien_id, tenant_id, sujet, methodes, authentifie_le, niveau, atteste_le)
                SELECT id, tenant_id, :sujet, string_to_array(:methodes, ',')::text[], :auth, :niveau, :le FROM lien_signature WHERE id = :lien""",
                new MapSqlParameterSource().addValue("lien", a.lien()).addValue("sujet", a.sujet()).addValue("methodes", String.join(",", a.methodes()))
                        .addValue("auth", a.authentifieLe() == null ? null : Timestamp.from(a.authentifieLe())).addValue("niveau", a.niveau())
                        .addValue("le", Timestamp.from(a.attesteLe())));
    }

    @Override
    public Optional<AttestationRetenue> duLien(UUID lien) {
        return jdbc.query("SELECT * FROM attestation_lien WHERE lien_id = :lien", new MapSqlParameterSource("lien", lien), (rs, n) -> {
            Timestamp auth = rs.getTimestamp("authentifie_le");
            return new AttestationRetenue(lien, rs.getString("sujet"), Arrays.asList((String[]) rs.getArray("methodes").getArray()),
                    auth == null ? null : auth.toInstant(), rs.getString("niveau"), rs.getTimestamp("atteste_le").toInstant());
        }).stream().findFirst();
    }
}
