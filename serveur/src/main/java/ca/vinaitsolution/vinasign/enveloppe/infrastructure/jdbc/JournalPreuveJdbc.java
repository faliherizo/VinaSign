package ca.vinaitsolution.vinasign.enveloppe.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalPreuve;
import ca.vinaitsolution.vinasign.enveloppe.domain.Ancrage;
import ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Journal de preuve dans PostgreSQL : maillons sous la RLS de l'organisation ; agrégat recalculé par la fonction
 * {@code journal_agreger} (toutes organisations, empreintes seulement) ; ancrages en ajout seul.
 */
@Component
class JournalPreuveJdbc implements JournalPreuve {

    private final NamedParameterJdbcTemplate jdbc;

    JournalPreuveJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChaineScellee.Maillon> maillons(IdentifiantEnveloppe enveloppe) {
        return jdbc.query("""
                SELECT sequence, type, acteur, (extract(epoch FROM survenu_le) * 1000000)::bigint AS micro, donnees::text AS donnees,
                       empreinte_precedente, empreinte
                FROM evenement_enveloppe WHERE enveloppe_id = :e ORDER BY sequence""", new MapSqlParameterSource("e", enveloppe.valeur()),
                (rs, n) -> new ChaineScellee.Maillon(rs.getLong("sequence"), rs.getString("type"), rs.getString("acteur"), rs.getLong("micro"),
                        rs.getString("donnees"), rs.getString("empreinte_precedente"), rs.getString("empreinte")));
    }

    @Override
    public Optional<Instant> derniereConsignation(IdentifiantEnveloppe enveloppe) {
        return Optional.ofNullable(jdbc.queryForObject("SELECT max(consigne_le) FROM evenement_enveloppe WHERE enveloppe_id = :e",
                new MapSqlParameterSource("e", enveloppe.valeur()), Timestamp.class)).map(Timestamp::toInstant);
    }

    @Override
    public Agregat agreger(Instant depuis, Instant jusqua) {
        return jdbc.queryForObject("SELECT * FROM journal_agreger(:depuis, :jusqua)",
                new MapSqlParameterSource("depuis", Timestamp.from(depuis)).addValue("jusqua", Timestamp.from(jusqua)),
                (rs, n) -> new Agregat(rs.getInt("nombre"), rs.getString("empreinte_agregee")));
    }

    @Override
    public Optional<Ancrage> dernierAncrage() {
        return jdbc.query("SELECT * FROM ancrage_journal ORDER BY jusqua DESC LIMIT 1", (rs, n) -> ancrage(rs)).stream().findFirst();
    }

    @Override
    public Optional<Ancrage> couvrant(Instant consigneLe) {
        return jdbc.query("SELECT * FROM ancrage_journal WHERE depuis < :le AND jusqua >= :le ORDER BY jusqua LIMIT 1",
                new MapSqlParameterSource("le", Timestamp.from(consigneLe)), (rs, n) -> ancrage(rs)).stream().findFirst();
    }

    @Override
    public void ajouter(Ancrage a) {
        jdbc.update("""
                INSERT INTO ancrage_journal (id, depuis, jusqua, nombre, empreinte_agregee, signature, ancre_le)
                VALUES (:id, :depuis, :jusqua, :nombre, :empreinte, :signature, :le)""", new MapSqlParameterSource("id", a.id())
                .addValue("depuis", Timestamp.from(a.depuis())).addValue("jusqua", Timestamp.from(a.jusqua())).addValue("nombre", a.nombre())
                .addValue("empreinte", a.empreinteAgregee()).addValue("signature", a.signature()).addValue("le", Timestamp.from(a.ancreLe())));
    }

    private static Ancrage ancrage(ResultSet rs) throws SQLException {
        return new Ancrage(rs.getObject("id", UUID.class), rs.getTimestamp("depuis").toInstant(), rs.getTimestamp("jusqua").toInstant(),
                rs.getInt("nombre"), rs.getString("empreinte_agregee"), rs.getBytes("signature"), rs.getTimestamp("ancre_le").toInstant());
    }
}
