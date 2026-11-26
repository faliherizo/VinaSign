package ca.vinaitsolution.vinasign.session.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.session.application.port.VerificationsIdentite;
import ca.vinaitsolution.vinasign.session.domain.VerificationIdentite;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Vérifications d'identité dans PostgreSQL, sous la RLS ; la référence désigne l'organisation par une fonction de plateforme. */
@Component
class VerificationsIdentiteJdbc implements VerificationsIdentite {

    private final NamedParameterJdbcTemplate jdbc;

    VerificationsIdentiteJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(VerificationIdentite v) {
        jdbc.update("""
                INSERT INTO verification_identite (id, tenant_id, session_id, signataire_id, fournisseur, reference, etat, debut)
                SELECT :id, tenant_id, id, :signataire, :fournisseur, :reference, :etat, :debut FROM session_signataire WHERE id = :session""",
                new MapSqlParameterSource("id", v.id()).addValue("session", v.session()).addValue("signataire", v.signataire())
                        .addValue("fournisseur", v.fournisseur()).addValue("reference", v.reference()).addValue("etat", v.etat().code())
                        .addValue("debut", Timestamp.from(v.debut())));
    }

    @Override
    public void remplacer(VerificationIdentite v) {
        jdbc.update("UPDATE verification_identite SET etat = :etat, methode = :methode, assurance = :assurance, fin = :fin WHERE id = :id",
                new MapSqlParameterSource("id", v.id()).addValue("etat", v.etat().code()).addValue("methode", v.methode())
                        .addValue("assurance", v.assurance()).addValue("fin", v.fin() == null ? null : Timestamp.from(v.fin())));
    }

    @Override
    public Optional<VerificationIdentite> parId(UUID id) {
        return jdbc.query("SELECT * FROM verification_identite WHERE id = :id", new MapSqlParameterSource("id", id), (rs, n) -> lire(rs))
                .stream().findFirst();
    }

    @Override
    public Optional<VerificationIdentite> parReference(String reference) {
        return jdbc.query("SELECT * FROM verification_identite WHERE reference = :r", new MapSqlParameterSource("r", reference), (rs, n) -> lire(rs))
                .stream().findFirst();
    }

    @Override
    public Optional<UUID> organisationDe(String reference) {
        return Optional.ofNullable(jdbc.queryForObject("SELECT verification_identite_organisation(:r)", new MapSqlParameterSource("r", reference),
                UUID.class));
    }

    private static VerificationIdentite lire(ResultSet rs) throws SQLException {
        Timestamp fin = rs.getTimestamp("fin");
        return new VerificationIdentite(rs.getObject("id", UUID.class), rs.getObject("session_id", UUID.class), rs.getObject("signataire_id", UUID.class),
                rs.getString("fournisseur"), rs.getString("reference"), VerificationIdentite.Etat.deCode(rs.getString("etat")), rs.getString("methode"),
                rs.getString("assurance"), rs.getTimestamp("debut").toInstant(), fin == null ? null : fin.toInstant());
    }
}
