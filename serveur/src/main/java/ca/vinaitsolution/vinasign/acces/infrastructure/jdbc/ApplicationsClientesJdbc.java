package ca.vinaitsolution.vinasign.acces.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.application.port.RegistreApplications;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.CleDejaEnregistree;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.OrigineIntegration;
import ca.vinaitsolution.vinasign.acces.domain.StatutApplication;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applications clientes dans PostgreSQL, sous la RLS de l'organisation courante ; la recherche par client_id passe par
 * la fonction d'exception à la RLS (point de jeton). Les empreintes de clés sont uniques sur toute la plateforme.
 */
@Component
class ApplicationsClientesJdbc implements ApplicationsClientes, RegistreApplications {

    private final NamedParameterJdbcTemplate jdbc;

    ApplicationsClientesJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ApplicationCliente> activeParClient(IdentifiantClient client) {
        return jdbc.query("SELECT * FROM acces_application_active(:client)", new MapSqlParameterSource("client", client.valeur()),
                (rs, n) -> application(rs)).stream().findFirst();
    }

    @Override
    public void ajouter(ApplicationCliente a, ClesPubliques cles) {
        JdbcTemplate brut = jdbc.getJdbcTemplate();
        brut.execute((ConnectionCallback<Void>) c -> {
            inserer(c, a);
            return null;
        });
        reserver(a, cles);
    }

    @Override
    public Optional<ApplicationCliente> deLOrganisation(IdentifiantClient client) {
        return jdbc.query("SELECT * FROM application_cliente WHERE client_id = :client",
                new MapSqlParameterSource("client", client.valeur()), (rs, n) -> application(rs)).stream().findFirst();
    }

    @Override
    public void remplacer(ApplicationCliente a) {
        jdbc.update("UPDATE application_cliente SET statut = :statut, revoque_le = :revoque WHERE client_id = :client",
                new MapSqlParameterSource().addValue("statut", a.statut().name().toLowerCase(Locale.ROOT))
                        .addValue("revoque", a.revoqueLe() == null ? null : Timestamp.from(a.revoqueLe()))
                        .addValue("client", a.client().valeur()));
    }

    @Override
    public void remplacerCles(ApplicationCliente a, ClesPubliques cles) {
        jdbc.update("UPDATE application_cliente SET jwks = CAST(:jwks AS jsonb) WHERE client_id = :client",
                new MapSqlParameterSource().addValue("jwks", a.clesPubliques()).addValue("client", a.client().valeur()));
        jdbc.update("DELETE FROM empreinte_cle_application WHERE client_id = :client", new MapSqlParameterSource("client", a.client().valeur()));
        reserver(a, cles);
    }

    private void reserver(ApplicationCliente a, ClesPubliques cles) {
        try {
            for (String empreinte : cles.empreintes()) {
                jdbc.getJdbcTemplate().update("INSERT INTO empreinte_cle_application (empreinte, client_id) VALUES (?, ?)", empreinte,
                        a.client().valeur());
            }
        } catch (DuplicateKeyException e) {
            throw new CleDejaEnregistree();
        }
    }

    @Override
    public List<ApplicationCliente> deLOrganisationCourante() {
        return jdbc.query("SELECT * FROM application_cliente ORDER BY nom, client_id", (rs, n) -> application(rs));
    }

    private static void inserer(Connection c, ApplicationCliente a) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO application_cliente (id, tenant_id, client_id, nom, environnement, jwks, portees, origines_integration, statut)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)""")) {
            ps.setObject(1, a.id());
            ps.setObject(2, a.organisation().valeur());
            ps.setString(3, a.client().valeur());
            ps.setString(4, a.nom());
            ps.setString(5, a.environnement());
            ps.setString(6, a.clesPubliques());
            ps.setArray(7, c.createArrayOf("text", a.portees().toArray()));
            ps.setArray(8, c.createArrayOf("text", a.origines().stream().map(OrigineIntegration::valeur).toArray()));
            ps.setString(9, a.statut().name().toLowerCase(Locale.ROOT));
            ps.executeUpdate();
        }
    }

    static ApplicationCliente application(ResultSet rs) throws SQLException {
        Timestamp revoque = rs.getTimestamp("revoque_le");
        return new ApplicationCliente(rs.getObject("id", UUID.class), new IdentifiantOrganisation(rs.getObject("tenant_id", UUID.class)),
                new IdentifiantClient(rs.getString("client_id")), rs.getString("nom"), rs.getString("environnement"),
                rs.getString("jwks"), Set.of(textes(rs.getArray("portees"))),
                Arrays.stream(textes(rs.getArray("origines_integration"))).map(OrigineIntegration::new).collect(Collectors.toSet()),
                StatutApplication.valueOf(rs.getString("statut").toUpperCase(Locale.ROOT)), revoque == null ? null : revoque.toInstant());
    }

    private static String[] textes(Array tableau) throws SQLException {
        return (String[]) tableau.getArray();
    }
}
