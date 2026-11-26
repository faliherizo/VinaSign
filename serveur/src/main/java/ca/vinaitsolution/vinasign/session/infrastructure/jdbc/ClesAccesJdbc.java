package ca.vinaitsolution.vinasign.session.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.session.application.port.ClesAcces;
import ca.vinaitsolution.vinasign.session.domain.CleAcces;
import ca.vinaitsolution.vinasign.session.domain.DefiCleAcces;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Clés d'accès et défis dans PostgreSQL, sous la RLS de l'organisation du signataire ; l'organisation vient de la session. */
@Component
class ClesAccesJdbc implements ClesAcces {

    private final NamedParameterJdbcTemplate jdbc;

    ClesAccesJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> niveauSession(UUID session) {
        return jdbc.queryForList("SELECT niveau_atteint FROM session_signataire WHERE id = :s AND niveau_atteint IS NOT NULL",
                new MapSqlParameterSource("s", session), String.class).stream().findFirst();
    }

    @Override
    public void defier(DefiCleAcces d) {
        jdbc.update("""
                INSERT INTO defi_cle_acces (session_id, tenant_id, nature, defi, expire_le)
                SELECT id, tenant_id, :nature, :defi, :expire FROM session_signataire WHERE id = :s
                ON CONFLICT (session_id) DO UPDATE SET nature = EXCLUDED.nature, defi = EXCLUDED.defi, expire_le = EXCLUDED.expire_le""",
                new MapSqlParameterSource("s", d.session()).addValue("nature", d.nature()).addValue("defi", d.defi())
                        .addValue("expire", Timestamp.from(d.expireLe())));
    }

    @Override
    public Optional<DefiCleAcces> consommerDefi(UUID session) {
        return jdbc.query("DELETE FROM defi_cle_acces WHERE session_id = :s RETURNING nature, defi, expire_le", new MapSqlParameterSource("s", session),
                (rs, n) -> new DefiCleAcces(session, rs.getString("nature"), rs.getBytes("defi"), rs.getTimestamp("expire_le").toInstant())).stream().findFirst();
    }

    @Override
    public List<CleAcces> de(String identite) {
        return jdbc.query("SELECT * FROM cle_acces WHERE identite = :i ORDER BY cree_le", new MapSqlParameterSource("i", identite),
                (rs, n) -> new CleAcces(rs.getObject("id", UUID.class), rs.getString("identite"), rs.getString("credential_id"), rs.getBytes("donnees"),
                        rs.getLong("compteur"), rs.getTimestamp("cree_le").toInstant()));
    }

    @Override
    public void ajouter(CleAcces c) {
        jdbc.update("""
                INSERT INTO cle_acces (id, tenant_id, identite, credential_id, donnees, compteur, cree_le)
                VALUES (:id, NULLIF(current_setting('app.organisation', true), '')::uuid, :i, :cred, :d, :c, :le)""",
                new MapSqlParameterSource("id", c.id()).addValue("i", c.identite()).addValue("cred", c.credentialId()).addValue("d", c.donnees())
                        .addValue("c", c.compteur()).addValue("le", Timestamp.from(c.creeLe())));
    }

    @Override
    public void utiliser(UUID cle, long compteur, Instant le) {
        jdbc.update("UPDATE cle_acces SET compteur = :c, utilisee_le = :le WHERE id = :id",
                new MapSqlParameterSource("id", cle).addValue("c", compteur).addValue("le", Timestamp.from(le)));
    }

    @Override
    public void authentifier(UUID session, String niveau, String amr) {
        jdbc.update("UPDATE session_signataire SET niveau_atteint = :n, amr = :a WHERE id = :s",
                new MapSqlParameterSource("s", session).addValue("n", niveau).addValue("a", amr));
    }
}
