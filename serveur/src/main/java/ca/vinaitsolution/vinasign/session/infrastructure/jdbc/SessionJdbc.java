package ca.vinaitsolution.vinasign.session.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.session.application.port.Consentements;
import ca.vinaitsolution.vinasign.session.application.port.LiensSignature;
import ca.vinaitsolution.vinasign.session.application.port.SessionsSignataire;
import ca.vinaitsolution.vinasign.session.domain.Consentement;
import ca.vinaitsolution.vinasign.session.domain.LienResolu;
import ca.vinaitsolution.vinasign.session.domain.LienSignature;
import ca.vinaitsolution.vinasign.session.domain.SessionSignataire;
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

/** Liens, sessions et consentements dans PostgreSQL, sous la RLS ; la recherche par jeton passe par ses fonctions. */
@Component
class SessionJdbc implements LiensSignature, SessionsSignataire, Consentements, ca.vinaitsolution.vinasign.session.application.port.RelancesDues {

    private final NamedParameterJdbcTemplate jdbc;

    SessionJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(LienSignature l) {
        jdbc.update("""
                INSERT INTO lien_signature (id, tenant_id, enveloppe_id, signataire_id, empreinte_jeton, expire_le, utilisations, utilisations_max, actions,
                                            rang_relance)
                SELECT :id, tenant_id, id, :signataire, :jeton, :expire, :utilisations, :max, string_to_array(:actions, ',')::text[], :rang
                FROM enveloppe WHERE id = :enveloppe""",
                new MapSqlParameterSource().addValue("id", l.id()).addValue("enveloppe", l.enveloppe()).addValue("signataire", l.signataire())
                        .addValue("jeton", l.empreinteJeton()).addValue("expire", Timestamp.from(l.expireLe()))
                        .addValue("utilisations", l.utilisations()).addValue("max", l.utilisationsMax())
                        .addValue("actions", String.join(",", new java.util.TreeSet<>(l.actions()))).addValue("rang", l.rang()));
    }

    @Override
    public void remplacer(LienSignature l) {
        jdbc.update("UPDATE lien_signature SET utilisations = :u, revoque_le = :r, consomme_le = :c WHERE id = :id",
                new MapSqlParameterSource().addValue("id", l.id()).addValue("u", l.utilisations()).addValue("r", ts(l.revoqueLe()))
                        .addValue("c", ts(l.consommeLe())));
    }

    @Override
    public Optional<LienResolu> parEmpreinte(String empreinte) {
        return jdbc.query("SELECT * FROM session_lien_par_jeton(:j)", new MapSqlParameterSource("j", empreinte),
                (rs, n) -> new LienResolu(lien(rs), rs.getObject("tenant_id", UUID.class))).stream().findFirst();
    }

    @Override
    public List<LienSignature> actifs(UUID signataire, Instant maintenant) {
        return jdbc.query("""
                SELECT * FROM lien_signature WHERE signataire_id = :s AND revoque_le IS NULL AND consomme_le IS NULL AND expire_le > :m""",
                new MapSqlParameterSource("s", signataire).addValue("m", Timestamp.from(maintenant)), (rs, n) -> lien(rs));
    }

    @Override
    public void revoquer(UUID lien, Instant le) {
        jdbc.update("UPDATE lien_signature SET revoque_le = :le WHERE id = :id AND revoque_le IS NULL AND consomme_le IS NULL",
                new MapSqlParameterSource("id", lien).addValue("le", Timestamp.from(le)));
    }

    @Override
    public List<LienActif> liensActifs(Instant maintenant, int limite) {
        return jdbc.query("SELECT * FROM relances_liens_actifs(:m, :l)", new MapSqlParameterSource("m", Timestamp.from(maintenant)).addValue("l", limite),
                (rs, n) -> new LienActif(rs.getObject("tenant_id", UUID.class), rs.getObject("enveloppe_id", UUID.class),
                        rs.getObject("signataire_id", UUID.class), rs.getObject("id", UUID.class), rs.getTimestamp("emis_le").toInstant(),
                        rs.getInt("rang_relance")));
    }

    @Override
    public int revoquerDe(UUID enveloppe, Instant le) {
        return jdbc.update("UPDATE lien_signature SET revoque_le = :le WHERE enveloppe_id = :e AND revoque_le IS NULL AND consomme_le IS NULL",
                new MapSqlParameterSource("e", enveloppe).addValue("le", Timestamp.from(le)));
    }

    @Override
    public void ajouter(SessionSignataire s) {
        jdbc.update("""
                INSERT INTO session_signataire (id, tenant_id, enveloppe_id, signataire_id, empreinte_jeton, expire_le)
                VALUES (:id, :org, :e, :s, :j, :expire)""", new MapSqlParameterSource().addValue("id", s.id()).addValue("org", s.organisation())
                .addValue("e", s.enveloppe()).addValue("s", s.signataire()).addValue("j", s.empreinteJeton()).addValue("expire", Timestamp.from(s.expireLe())));
    }

    @Override
    public Optional<SessionSignataire> session(String empreinte) {
        return jdbc.query("SELECT * FROM session_par_jeton(:j)", new MapSqlParameterSource("j", empreinte),
                (rs, n) -> new SessionSignataire(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("enveloppe_id", UUID.class), rs.getObject("signataire_id", UUID.class), rs.getString("empreinte_jeton"),
                        rs.getTimestamp("expire_le").toInstant())).stream().findFirst();
    }

    @Override
    public void ajouter(Consentement c) {
        jdbc.update("""
                INSERT INTO consentement (id, tenant_id, enveloppe_id, signataire_id, version_texte, empreinte_texte, accepte_le)
                SELECT :id, tenant_id, id, :s, :v, :h, :le FROM enveloppe WHERE id = :e""", new MapSqlParameterSource().addValue("id", c.id())
                .addValue("e", c.enveloppe()).addValue("s", c.signataire()).addValue("v", c.versionTexte()).addValue("h", c.empreinteTexte())
                .addValue("le", Timestamp.from(c.accepteLe())));
    }

    @Override
    public Optional<Consentement> dernier(UUID signataire) {
        return jdbc.query("SELECT * FROM consentement WHERE signataire_id = :s ORDER BY accepte_le DESC LIMIT 1", new MapSqlParameterSource("s", signataire),
                (rs, n) -> new Consentement(rs.getObject("id", UUID.class), rs.getObject("enveloppe_id", UUID.class), signataire,
                        rs.getString("version_texte"), rs.getString("empreinte_texte"), rs.getTimestamp("accepte_le").toInstant())).stream().findFirst();
    }

    private static LienSignature lien(ResultSet rs) throws SQLException {
        return new LienSignature(rs.getObject("id", UUID.class), rs.getObject("enveloppe_id", UUID.class), rs.getObject("signataire_id", UUID.class),
                rs.getString("empreinte_jeton"), rs.getTimestamp("expire_le").toInstant(), rs.getInt("utilisations"), rs.getInt("utilisations_max"),
                instant(rs.getTimestamp("revoque_le")), instant(rs.getTimestamp("consomme_le")),
                java.util.Set.of((String[]) rs.getArray("actions").getArray()), rs.getInt("rang_relance"));
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
