package ca.vinaitsolution.vinasign.referentiel.infrastructure.jdbc;

import ca.vinaitsolution.vinasign.commun.domain.Libelles;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Référentiels lus dans PostgreSQL ({@code referentiel_type}, {@code referentiel_valeur}). Requêtes paramétrées seulement.
 * La RLS ne laisse voir que les valeurs communes et celles de l'organisation courante (VS-005) ; une valeur propre à
 * l'organisation remplace la valeur commune de même code.
 */
@Component
class ReferentielsJdbc implements Referentiels {

    private static final TypeReference<Map<String, String>> CARTE = new TypeReference<>() { };
    private static final String TYPES = "SELECT id, code, libelles FROM referentiel_type ORDER BY code";
    private static final String TYPE = "SELECT id, code, libelles FROM referentiel_type WHERE code = :code";
    private static final String VALEURS = """
            SELECT DISTINCT ON (code) code, libelles, ordre, attributs, actif FROM referentiel_valeur
            WHERE type_id = :type ORDER BY code, tenant_id NULLS LAST""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    ReferentielsJdbc(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<TypeReferentiel> types() {
        return jdbc.query(TYPES, (rs, n) -> entete(rs)).stream().map(this::avecValeurs).toList();
    }

    @Override
    public Optional<TypeReferentiel> type(CodeReferentiel code) {
        return jdbc.query(TYPE, new MapSqlParameterSource("code", code.valeur()), (rs, n) -> entete(rs)).stream().findFirst().map(this::avecValeurs);
    }

    /** Ligne de referentiel_type, lue entièrement avant toute autre requête. */
    private record Entete(Object id, String code, String libelles) {
    }

    private static Entete entete(ResultSet rs) throws SQLException {
        return new Entete(rs.getObject("id"), rs.getString("code"), rs.getString("libelles"));
    }

    /**
     * Les valeurs se lisent après que le résultat des types a rendu sa connexion : une requête lancée depuis le lecteur
     * de lignes d'une autre emprunte une seconde connexion pendant que la première est tenue, et une rafale de lectures
     * bloque alors tout le pool (chacune tient une connexion et attend l'autre).
     */
    private TypeReferentiel avecValeurs(Entete t) {
        List<ValeurReferentiel> valeurs = new ArrayList<>(jdbc.query(VALEURS,
                new MapSqlParameterSource("type", t.id()), (v, n) -> new ValeurReferentiel(
                        new CodeReferentiel(v.getString("code")), Libelles.de(carte(v.getString("libelles"))),
                        v.getInt("ordre"), carte(v.getString("attributs")), v.getBoolean("actif"))));
        return new TypeReferentiel(new CodeReferentiel(t.code()), Libelles.de(carte(t.libelles())), valeurs);
    }

    /** Un JSON illisible en base est une corruption : elle se signale, elle ne se masque pas en liste vide. */
    Map<String, String> carte(String brut) {
        if (brut == null) {
            return Map.of();
        }
        try {
            return new LinkedHashMap<>(json.readValue(brut, CARTE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Référentiel illisible en base : JSON invalide.", e);
        }
    }
}
