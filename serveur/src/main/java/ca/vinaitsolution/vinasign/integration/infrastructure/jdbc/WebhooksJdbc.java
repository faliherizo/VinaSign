package ca.vinaitsolution.vinasign.integration.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.integration.application.port.DestinationsWebhook;
import ca.vinaitsolution.vinasign.integration.application.port.Livraisons;
import ca.vinaitsolution.vinasign.integration.domain.DestinationWebhook;
import ca.vinaitsolution.vinasign.integration.domain.Livraison;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Destinations et livraisons dans PostgreSQL : RLS de l'organisation ; répartition et livraisons dues par fonctions de plateforme. */
@Component
class WebhooksJdbc implements DestinationsWebhook, Livraisons {

    private final NamedParameterJdbcTemplate jdbc;

    WebhooksJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(DestinationWebhook d, String secretChiffre) {
        jdbc.update("""
                INSERT INTO webhook_destination (id, tenant_id, client_id, url, secret_chiffre, evenements, cree_le)
                VALUES (:id, :org, :client, :url, :secret, string_to_array(:evenements, ',')::text[], :le)""",
                new MapSqlParameterSource("id", d.id()).addValue("org", d.organisation()).addValue("client", d.client()).addValue("url", d.url())
                        .addValue("secret", secretChiffre).addValue("evenements", String.join(",", d.evenements())).addValue("le", Timestamp.from(d.creeLe())));
    }

    @Override
    public List<DestinationWebhook> actives(String client) {
        return jdbc.query("SELECT * FROM webhook_destination WHERE client_id = :c AND retire_le IS NULL ORDER BY cree_le",
                new MapSqlParameterSource("c", client), (rs, n) -> destination(rs));
    }

    @Override
    public Optional<DestinationWebhook> active(UUID id) {
        return jdbc.query("SELECT * FROM webhook_destination WHERE id = :id AND retire_le IS NULL", new MapSqlParameterSource("id", id),
                (rs, n) -> destination(rs)).stream().findFirst();
    }

    @Override
    public void retirer(UUID id, Instant le) {
        jdbc.update("UPDATE webhook_destination SET retire_le = :le WHERE id = :id", new MapSqlParameterSource("id", id).addValue("le", Timestamp.from(le)));
    }

    @Override
    public int repartir(Instant le) {
        Integer n = jdbc.queryForObject("SELECT webhooks_repartir(:le)", new MapSqlParameterSource("le", Timestamp.from(le)), Integer.class);
        return n == null ? 0 : n;
    }

    @Override
    public List<Due> dues(Instant le, int limite) {
        return jdbc.query("SELECT * FROM webhooks_dus(:le, :limite)", new MapSqlParameterSource("le", Timestamp.from(le)).addValue("limite", limite),
                (rs, n) -> new Due(new Livraison(rs.getObject("id", UUID.class), rs.getObject("destination_id", UUID.class),
                        rs.getObject("evenement_sortant_id", UUID.class), instant(rs, "cree_le"), rs.getInt("tentatives"), instant(rs, "prochaine_tentative"),
                        null, null, null), rs.getObject("tenant_id", UUID.class), rs.getString("url"), rs.getString("secret_chiffre"), rs.getString("nom"),
                        rs.getObject("enveloppe_id", UUID.class), instant(rs, "survenu_le")));
    }

    @Override
    public void remplacer(Livraison l) {
        jdbc.update("""
                UPDATE livraison_webhook SET tentatives = :t, prochaine_tentative = :p, livre_le = :livre, abandonne_le = :abandon, dernier_code = :code
                WHERE id = :id""", new MapSqlParameterSource("id", l.id()).addValue("t", l.tentatives()).addValue("p", Timestamp.from(l.prochaineTentative()))
                .addValue("livre", horodatage(l.livreLe())).addValue("abandon", horodatage(l.abandonneLe())).addValue("code", l.dernierCode()));
    }

    @Override
    public List<Livraison> de(UUID destination, int limite) {
        return jdbc.query("SELECT * FROM livraison_webhook WHERE destination_id = :d ORDER BY cree_le DESC, id LIMIT :l",
                new MapSqlParameterSource("d", destination).addValue("l", limite), (rs, n) -> new Livraison(rs.getObject("id", UUID.class), destination,
                        rs.getObject("evenement_sortant_id", UUID.class), instant(rs, "cree_le"), rs.getInt("tentatives"), instant(rs, "prochaine_tentative"),
                        instant(rs, "livre_le"), instant(rs, "abandonne_le"), (Integer) rs.getObject("dernier_code")));
    }

    private static DestinationWebhook destination(ResultSet rs) throws SQLException {
        return new DestinationWebhook(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("client_id"), rs.getString("url"),
                Set.of((String[]) rs.getArray("evenements").getArray()), instant(rs, "cree_le"));
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        Timestamp t = rs.getTimestamp(colonne);
        return t == null ? null : t.toInstant();
    }

    private static Timestamp horodatage(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
