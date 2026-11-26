package ca.vinaitsolution.vinasign.enveloppe.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.enveloppe.application.port.ArchivesDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.EtatArchive;
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

/** États archivés dans PostgreSQL, sous la RLS ; la recherche des échéances passe par une fonction de plateforme. */
@Component
class ArchivesDocumentsJdbc implements ArchivesDocuments {

    private final NamedParameterJdbcTemplate jdbc;

    ArchivesDocumentsJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ARenouveler> aRenouveler(Instant limite, int lot) {
        return jdbc.query("SELECT id, tenant_id FROM archives_a_renouveler(:limite, :lot)",
                new MapSqlParameterSource("limite", Timestamp.from(limite)).addValue("lot", lot),
                (rs, n) -> new ARenouveler(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class)));
    }

    @Override
    public void ajouter(EtatArchive a) {
        jdbc.update("""
                INSERT INTO archive_document (id, tenant_id, enveloppe_id, cle_objet, sha256, taille, echeance, cree_le)
                SELECT :id, tenant_id, id, :cle, :sha, :taille, :echeance, :le FROM enveloppe WHERE id = :e""",
                new MapSqlParameterSource("id", a.id()).addValue("e", a.enveloppe().valeur()).addValue("cle", a.cleObjet()).addValue("sha", a.sha256())
                        .addValue("taille", a.taille()).addValue("echeance", a.echeance() == null ? null : Timestamp.from(a.echeance()))
                        .addValue("le", Timestamp.from(a.creeLe())));
    }

    @Override
    public Optional<EtatArchive> dernier(IdentifiantEnveloppe enveloppe) {
        return jdbc.query("SELECT * FROM archive_document WHERE enveloppe_id = :e ORDER BY cree_le DESC LIMIT 1",
                new MapSqlParameterSource("e", enveloppe.valeur()), (rs, n) -> etat(rs)).stream().findFirst();
    }

    private static EtatArchive etat(ResultSet rs) throws SQLException {
        Timestamp echeance = rs.getTimestamp("echeance");
        return new EtatArchive(rs.getObject("id", UUID.class), new IdentifiantEnveloppe(rs.getObject("enveloppe_id", UUID.class)),
                rs.getString("cle_objet"), rs.getString("sha256"), rs.getLong("taille"), echeance == null ? null : echeance.toInstant(),
                rs.getTimestamp("cree_le").toInstant());
    }
}
