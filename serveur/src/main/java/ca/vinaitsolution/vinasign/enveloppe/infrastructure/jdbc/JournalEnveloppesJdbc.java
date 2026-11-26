package ca.vinaitsolution.vinasign.enveloppe.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Journal des enveloppes ; dans la même transaction, la boîte d'envoi reçoit l'événement si son type porte l'attribut
 * {@code webhook} dans le référentiel {@code evenement-enveloppe} : publier un événement de plus est une donnée.
 */
@Component
class JournalEnveloppesJdbc implements JournalEnveloppes {

    private static final TypeReference<Map<String, String>> DONNEES = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    JournalEnveloppesJdbc(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void consigner(EvenementEnveloppe e) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", e.id()).addValue("org", e.organisation().valeur())
                .addValue("enveloppe", e.enveloppe().valeur()).addValue("type", e.type()).addValue("acteur", e.acteur())
                .addValue("le", Timestamp.from(e.survenuLe())).addValue("donnees", ecrire(e.donnees())).addValue("sortant", UUID.randomUUID());
        jdbc.update("""
                INSERT INTO evenement_enveloppe (id, tenant_id, enveloppe_id, type, acteur, survenu_le, donnees)
                VALUES (:id, :org, :enveloppe, :type, :acteur, :le, CAST(:donnees AS jsonb))""", p);
        jdbc.update("""
                INSERT INTO evenement_sortant (id, tenant_id, client_id, evenement_id, nom, cree_le)
                SELECT :sortant, e.tenant_id, e.client_id, :id, v.attributs ->> 'webhook', :le
                FROM enveloppe e, referentiel_valeur v JOIN referentiel_type t ON t.id = v.type_id
                WHERE e.id = :enveloppe AND t.code = 'evenement-enveloppe' AND v.code = :type AND v.actif AND v.attributs ->> 'webhook' IS NOT NULL
                ORDER BY v.tenant_id NULLS LAST LIMIT 1""", p);
    }

    @Override
    public List<EvenementEnveloppe> de(IdentifiantEnveloppe enveloppe) {
        return jdbc.query("SELECT * FROM evenement_enveloppe WHERE enveloppe_id = :e ORDER BY survenu_le, id",
                new MapSqlParameterSource("e", enveloppe.valeur()), (rs, n) -> new EvenementEnveloppe(rs.getObject("id", UUID.class),
                        enveloppe, new ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation(rs.getObject("tenant_id", UUID.class)),
                        rs.getString("type"), rs.getString("acteur"), rs.getTimestamp("survenu_le").toInstant(), lire(rs.getString("donnees"))));
    }

    private String ecrire(Map<String, String> donnees) {
        return ca.vinaitsolution.vinasign.commun.infrastructure.Json.ecrire(json, donnees, "Données d'événement");
    }

    private Map<String, String> lire(String texte) {
        return ca.vinaitsolution.vinasign.commun.infrastructure.Json.lire(json, texte, DONNEES, "Données d'événement");
    }
}
