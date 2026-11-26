package ca.vinaitsolution.vinasign.enveloppe.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.enveloppe.application.port.ListeEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.domain.StatutSignataire;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Liste paginée par clé (date de création, identifiant), sous la RLS : jamais les enveloppes d'une autre organisation. */
@Component
class ListeEnveloppesJdbc implements ListeEnveloppes {

    private static final String REQUETE = """
            SELECT e.id, e.reference_externe, e.mode, e.statut, e.expire_le, e.cree_le,
                   (SELECT count(*) FROM signataire s WHERE s.enveloppe_id = e.id) AS signataires,
                   (SELECT count(*) FROM signataire s WHERE s.enveloppe_id = e.id AND s.statut = :signe) AS signes
            FROM enveloppe e
            WHERE e.client_id = :application
              AND (CAST(:statut AS VARCHAR) IS NULL OR e.statut = :statut)
              AND (CAST(:apresLe AS TIMESTAMPTZ) IS NULL OR (e.cree_le, e.id) < (:apresLe, :apresId))
            ORDER BY e.cree_le DESC, e.id DESC
            LIMIT :limite
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ListeEnveloppesJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Resume> de(String application, Optional<String> statut, Optional<Position> apres, int limite) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("application", application).addValue("statut", statut.orElse(null))
                .addValue("apresLe", apres.map(a -> Timestamp.from(a.creeLe())).orElse(null))
                .addValue("apresId", apres.map(Position::id).orElse(null)).addValue("limite", limite)
                .addValue("signe", StatutSignataire.SIGNE.code());
        return jdbc.query(REQUETE, p, (rs, n) -> new Resume(rs.getObject("id", UUID.class), rs.getString("reference_externe"), rs.getString("mode"),
                rs.getString("statut"), rs.getTimestamp("expire_le").toInstant(), rs.getTimestamp("cree_le").toInstant(), rs.getInt("signataires"),
                rs.getInt("signes")));
    }
}
