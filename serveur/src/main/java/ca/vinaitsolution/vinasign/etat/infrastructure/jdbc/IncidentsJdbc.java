package ca.vinaitsolution.vinasign.etat.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.commun.infrastructure.Json;
import ca.vinaitsolution.vinasign.etat.application.port.Incidents;
import ca.vinaitsolution.vinasign.etat.domain.Incident;
import ca.vinaitsolution.vinasign.etat.domain.MiseAJourIncident;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Incidents dans PostgreSQL : données de la plateforme, publiques, hors RLS ; communications en ajout seul. */
@Component
class IncidentsJdbc implements Incidents {

    private static final TypeReference<Map<String, String>> TEXTES = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    IncidentsJdbc(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void ajouter(Incident i) {
        jdbc.update("""
                INSERT INTO incident (id, titre, impact, composants, debut, fin) VALUES (:id, CAST(:titre AS jsonb), :impact, :composants, :debut, :fin)""",
                new MapSqlParameterSource("id", i.id()).addValue("titre", ecrire(i.titre())).addValue("impact", i.impact())
                        .addValue("composants", i.composants().toArray(String[]::new)).addValue("debut", Timestamp.from(i.debut()))
                        .addValue("fin", i.fin() == null ? null : Timestamp.from(i.fin())));
        i.misesAJour().forEach(m -> ajouterMiseAJour(i.id(), m));
    }

    @Override
    public void publier(UUID incident, MiseAJourIncident m, Instant fin) {
        ajouterMiseAJour(incident, m);
        if (fin != null) {
            jdbc.update("UPDATE incident SET fin = :fin WHERE id = :id AND fin IS NULL",
                    new MapSqlParameterSource("id", incident).addValue("fin", Timestamp.from(fin)));
        }
    }

    private void ajouterMiseAJour(UUID incident, MiseAJourIncident m) {
        jdbc.update("""
                INSERT INTO incident_mise_a_jour (id, incident_id, phase, message, le) VALUES (:id, :incident, :phase, CAST(:message AS jsonb), :le)""",
                new MapSqlParameterSource("id", m.id()).addValue("incident", incident).addValue("phase", m.phase())
                        .addValue("message", ecrire(m.message())).addValue("le", Timestamp.from(m.le())));
    }

    @Override
    public Optional<Incident> parId(UUID id) {
        return lire("WHERE i.id = :id", new MapSqlParameterSource("id", id)).stream().findFirst();
    }

    @Override
    public List<Incident> depuis(Instant instant) {
        return lire("WHERE i.fin IS NULL OR i.fin >= :depuis", new MapSqlParameterSource("depuis", Timestamp.from(instant)));
    }

    private List<Incident> lire(String filtre, MapSqlParameterSource p) {
        Map<UUID, List<MiseAJourIncident>> communications = new LinkedHashMap<>();
        List<Entete> lignes = new ArrayList<>();
        jdbc.query("SELECT i.*, m.id AS m_id, m.phase, m.message, m.le FROM incident i LEFT JOIN incident_mise_a_jour m ON m.incident_id = i.id "
                + filtre + " ORDER BY i.debut DESC, m.le", p, rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    if (!communications.containsKey(id)) {
                        communications.put(id, new ArrayList<>());
                        lignes.add(entete(rs));
                    }
                    if (rs.getObject("m_id") != null) {
                        communications.get(id).add(new MiseAJourIncident(rs.getObject("m_id", UUID.class), rs.getString("phase"),
                                Libelles.de(textes(rs.getString("message"))), rs.getTimestamp("le").toInstant()));
                    }
                });
        return lignes.stream().map(l -> new Incident(l.id(), l.titre(), l.impact(), l.composants(), l.debut(), l.fin(), communications.get(l.id())))
                .toList();
    }

    /** Colonnes de l'incident, lues une fois par incident (la jointure répète la ligne par communication). */
    private record Entete(UUID id, Libelles titre, String impact, List<String> composants, Instant debut, Instant fin) {
    }

    private Entete entete(ResultSet rs) throws SQLException {
        Array composants = rs.getArray("composants");
        Timestamp fin = rs.getTimestamp("fin");
        return new Entete(rs.getObject("id", UUID.class), Libelles.de(textes(rs.getString("titre"))), rs.getString("impact"),
                Arrays.asList((String[]) composants.getArray()), rs.getTimestamp("debut").toInstant(), fin == null ? null : fin.toInstant());
    }

    private String ecrire(Libelles l) {
        Map<String, String> m = new LinkedHashMap<>();
        l.parLangue().forEach((langue, texte) -> m.put(langue.valeur(), texte));
        return Json.ecrire(json, m, "Texte d'incident");
    }

    private Map<String, String> textes(String brut) {
        return Json.lire(json, brut, TEXTES, "Texte d'incident");
    }
}
