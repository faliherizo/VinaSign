package ca.vinaitsolution.vinasign.support;

import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Écrit un référentiel en base comme le ferait l'administration de la plateforme : par le compte propriétaire (le rôle
 * applicatif n'a que la lecture), identifiants générés par la base.
 */
@TestComponent
public class ReferentielsSql {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ReferentielsSql(@LiquibaseDataSource DataSource proprietaire, ObjectMapper json) {
        this.jdbc = new NamedParameterJdbcTemplate(proprietaire);
        this.json = json;
    }

    /** Valeur propre à une organisation (surcharge d'une valeur commune, ou valeur supplémentaire). */
    public void ajouterValeurOrganisation(String typeCode, java.util.UUID organisation, ValeurReferentiel v) {
        jdbc.update("""
                INSERT INTO referentiel_valeur (type_id, tenant_id, code, libelles, ordre, attributs, actif)
                SELECT id, :org, :code, CAST(:libelles AS jsonb), :ordre, CAST(:attributs AS jsonb), :actif FROM referentiel_type WHERE code = :type""",
                new MapSqlParameterSource("type", typeCode).addValue("org", organisation).addValue("code", v.code().valeur())
                        .addValue("libelles", libelles(v.libelles())).addValue("ordre", v.ordre())
                        .addValue("attributs", ecrire(v.attributs())).addValue("actif", v.actif()));
    }

    public void enregistrer(TypeReferentiel type) {
        jdbc.update("INSERT INTO referentiel_type (code, libelles) VALUES (:code, CAST(:libelles AS jsonb))",
                new MapSqlParameterSource("code", type.code().valeur()).addValue("libelles", libelles(type.libelles())));
        type.valeurs().forEach(v -> ajouterValeur(type.code().valeur(), v));
    }

    public void ajouterValeur(String typeCode, ValeurReferentiel v) {
        jdbc.update("""
                INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs, actif)
                SELECT id, :code, CAST(:libelles AS jsonb), :ordre, CAST(:attributs AS jsonb), :actif FROM referentiel_type WHERE code = :type""",
                new MapSqlParameterSource("type", typeCode).addValue("code", v.code().valeur())
                        .addValue("libelles", libelles(v.libelles())).addValue("ordre", v.ordre())
                        .addValue("attributs", ecrire(v.attributs())).addValue("actif", v.actif()));
    }

    public void retirerValeur(String typeCode, String code) {
        jdbc.update("DELETE FROM referentiel_valeur WHERE code = :code AND type_id = (SELECT id FROM referentiel_type WHERE code = :type)",
                new MapSqlParameterSource("type", typeCode).addValue("code", code));
    }

    private String libelles(ca.vinaitsolution.vinasign.commun.domain.Libelles l) {
        Map<String, String> brut = new LinkedHashMap<>();
        l.parLangue().forEach((langue, texte) -> brut.put(langue.valeur(), texte));
        return ecrire(brut);
    }

    private String ecrire(Map<String, String> carte) {
        try {
            return json.writeValueAsString(carte);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
