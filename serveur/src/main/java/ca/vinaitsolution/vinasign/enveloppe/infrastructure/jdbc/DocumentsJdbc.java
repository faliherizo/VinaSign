package ca.vinaitsolution.vinasign.enveloppe.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.enveloppe.application.port.DepotsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.DepotDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionImmuable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Versions (ajout seul) et dépôts de documents dans PostgreSQL. */
@Component
class DocumentsJdbc implements VersionsDocuments, DepotsDocuments {

    private final NamedParameterJdbcTemplate jdbc;

    DocumentsJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(VersionDocument v) {
        try {
            jdbc.update("""
                    INSERT INTO version_document (id, tenant_id, enveloppe_id, nature, cle_objet, sha256, taille, pages, cree_le)
                    SELECT :id, tenant_id, id, :nature, :cle, :sha, :taille, :pages, :cree FROM enveloppe WHERE id = :enveloppe""",
                    new MapSqlParameterSource().addValue("id", v.id()).addValue("enveloppe", v.enveloppe().valeur()).addValue("nature", v.nature())
                            .addValue("cle", v.cleObjet()).addValue("sha", v.sha256()).addValue("taille", v.taille()).addValue("pages", v.pages())
                            .addValue("cree", Timestamp.from(v.creeLe())));
        } catch (DuplicateKeyException e) {
            throw new VersionImmuable();
        }
    }

    @Override
    public List<VersionDocument> de(IdentifiantEnveloppe enveloppe) {
        return jdbc.query("SELECT * FROM version_document WHERE enveloppe_id = :e ORDER BY cree_le",
                new MapSqlParameterSource("e", enveloppe.valeur()), (rs, n) -> version(rs));
    }

    @Override
    public void ajouter(DepotDocument d) {
        jdbc.update("""
                INSERT INTO depot_document (id, tenant_id, enveloppe_id, empreinte_jeton, cle_objet, expire_le)
                SELECT :id, tenant_id, id, :jeton, :cle, :expire FROM enveloppe WHERE id = :enveloppe""", new MapSqlParameterSource()
                .addValue("id", d.id()).addValue("enveloppe", d.enveloppe().valeur()).addValue("jeton", d.empreinteJeton())
                .addValue("cle", d.cleObjet()).addValue("expire", Timestamp.from(d.expireLe())));
    }

    @Override
    public Optional<DepotDocument> recevoir(String empreinteJeton, Instant maintenant) {
        return jdbc.query("SELECT * FROM enveloppe_recevoir_depot(:jeton, :maintenant)", new MapSqlParameterSource("jeton", empreinteJeton)
                .addValue("maintenant", Timestamp.from(maintenant)), (rs, n) -> depot(rs)).stream().findFirst();
    }

    @Override
    public Optional<DepotDocument> parId(UUID id) {
        return jdbc.query("SELECT * FROM depot_document WHERE id = :id", new MapSqlParameterSource("id", id), (rs, n) -> depot(rs))
                .stream().findFirst();
    }

    @Override
    public void confirmer(UUID id, Instant le) {
        jdbc.update("UPDATE depot_document SET confirme_le = :le WHERE id = :id AND confirme_le IS NULL",
                new MapSqlParameterSource("id", id).addValue("le", Timestamp.from(le)));
    }

    private static VersionDocument version(ResultSet rs) throws SQLException {
        return new VersionDocument(rs.getObject("id", UUID.class), new IdentifiantEnveloppe(rs.getObject("enveloppe_id", UUID.class)),
                rs.getString("nature"), rs.getString("cle_objet"), rs.getString("sha256"), rs.getLong("taille"), rs.getInt("pages"),
                rs.getTimestamp("cree_le").toInstant());
    }

    private static DepotDocument depot(ResultSet rs) throws SQLException {
        return new DepotDocument(rs.getObject("id", UUID.class), new IdentifiantEnveloppe(rs.getObject("enveloppe_id", UUID.class)),
                rs.getString("empreinte_jeton"), rs.getString("cle_objet"), rs.getTimestamp("expire_le").toInstant(),
                instant(rs.getTimestamp("recu_le")), instant(rs.getTimestamp("confirme_le")));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
