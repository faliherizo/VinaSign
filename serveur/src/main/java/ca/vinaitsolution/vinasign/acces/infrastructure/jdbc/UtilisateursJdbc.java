package ca.vinaitsolution.vinasign.acces.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.acces.application.port.AnnuaireConnexion;
import ca.vinaitsolution.vinasign.acces.application.port.UtilisateursPortail;
import ca.vinaitsolution.vinasign.acces.domain.Courriel;
import ca.vinaitsolution.vinasign.acces.domain.CourrielDejaInscrit;
import ca.vinaitsolution.vinasign.acces.domain.IdentiteExterne;
import ca.vinaitsolution.vinasign.acces.domain.OrganisationInactive;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Utilisateurs du portail dans PostgreSQL, sous la RLS ; l'authentification et l'amorçage passent par leurs fonctions. */
@Component
class UtilisateursJdbc implements UtilisateursPortail, AnnuaireConnexion {

    private final NamedParameterJdbcTemplate jdbc;

    UtilisateursJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(UtilisateurPortail u) {
        executer("INSERT INTO utilisateur_portail (id, tenant_id, courriel, roles, actif) VALUES (?, ?, ?, ?, true)", u);
    }

    @Override
    public void ajouterPourLaPlateforme(UtilisateurPortail u) {
        if (!executer("SELECT acces_inviter_pour_plateforme(?, ?, ?, ?)", u)) {
            throw new OrganisationInactive();
        }
    }

    @Override
    public Optional<UtilisateurPortail> parId(UUID id) {
        return jdbc.query("SELECT * FROM utilisateur_portail WHERE id = :id", new MapSqlParameterSource("id", id), (rs, n) -> utilisateur(rs))
                .stream().findFirst();
    }

    @Override
    public List<UtilisateurPortail> deLOrganisationCourante() {
        return jdbc.query("SELECT * FROM utilisateur_portail ORDER BY courriel", (rs, n) -> utilisateur(rs));
    }

    @Override
    public void remplacer(UtilisateurPortail u) {
        jdbc.getJdbcTemplate().execute((ConnectionCallback<Void>) c -> {
            try (var ps = c.prepareStatement("UPDATE utilisateur_portail SET roles = ?, actif = ? WHERE id = ?")) {
                ps.setArray(1, c.createArrayOf("text", u.roles().toArray()));
                ps.setBoolean(2, u.actif());
                ps.setObject(3, u.id());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<UtilisateurPortail> authentifier(IdentiteExterne identite) {
        return jdbc.query("SELECT * FROM acces_utilisateur_authentifie(:emetteur, :sujet, :courriel)", new MapSqlParameterSource()
                        .addValue("emetteur", identite.emetteur()).addValue("sujet", identite.sujet())
                        .addValue("courriel", identite.courrielVerifie().map(Courriel::valeur).orElse(null)),
                (rs, n) -> utilisateur(rs)).stream().findFirst();
    }

    /** Exécute l'insertion ; rend faux si la fonction d'amorçage n'a rien inséré (organisation inactive). */
    private boolean executer(String sql, UtilisateurPortail u) {
        try {
            return Boolean.TRUE.equals(jdbc.getJdbcTemplate().execute((ConnectionCallback<Boolean>) c -> inserer(c, sql, u)));
        } catch (DuplicateKeyException e) {
            throw new CourrielDejaInscrit();
        }
    }

    private static boolean inserer(Connection c, String sql, UtilisateurPortail u) throws SQLException {
        try (var ps = c.prepareStatement(sql)) {
            ps.setObject(1, u.id());
            ps.setObject(2, u.organisation().map(IdentifiantOrganisation::valeur).orElse(null));
            ps.setString(3, u.courriel().valeur());
            ps.setArray(4, c.createArrayOf("text", u.roles().toArray()));
            if (!ps.execute()) {
                return ps.getUpdateCount() > 0;
            }
            try (ResultSet rs = ps.getResultSet()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    static UtilisateurPortail utilisateur(ResultSet rs) throws SQLException {
        UUID organisation = rs.getObject("tenant_id", UUID.class);
        return new UtilisateurPortail(rs.getObject("id", UUID.class),
                Optional.ofNullable(organisation).map(IdentifiantOrganisation::new), new Courriel(rs.getString("courriel")),
                Set.of((String[]) rs.getArray("roles").getArray()), rs.getBoolean("actif"));
    }
}
