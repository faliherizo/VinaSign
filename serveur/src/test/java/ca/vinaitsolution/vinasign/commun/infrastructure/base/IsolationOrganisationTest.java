package ca.vinaitsolution.vinasign.commun.infrastructure.base;

import static ca.vinaitsolution.vinasign.support.Fabrique.codeUnique;
import static ca.vinaitsolution.vinasign.support.Fabrique.type;
import static ca.vinaitsolution.vinasign.support.Fabrique.valeur;
import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.support.OrganisationDeTest;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import ca.vinaitsolution.vinasign.support.ReferentielsSql;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * VS-005 — isolation des organisations par la base elle-même (RLS), en défense de l'application. Les tables à isoler
 * sont découvertes dans le catalogue : une nouvelle table portant {@code tenant_id} sans RLS fait échouer la construction.
 */
@SpringBootTest
@Import({PostgresDeTest.class, ReferentielsSql.class, OrganisationDeTest.class})
class IsolationOrganisationTest {

    private static final IdentifiantOrganisation A = new IdentifiantOrganisation(UUID.randomUUID());
    private static final IdentifiantOrganisation B = new IdentifiantOrganisation(UUID.randomUUID());

    @Autowired DataSource application;
    @Autowired @LiquibaseDataSource DataSource proprietaire;
    @Autowired BaseProprietes proprietes;
    @Autowired OrganisationDeTest organisation;
    @Autowired Referentiels referentiels;
    @Autowired ReferentielsSql sql;

    @AfterEach
    void anonyme() {
        organisation.anonyme();
    }

    @Test
    void toute_table_portant_tenant_id_a_la_rls_active_et_au_moins_une_politique() {
        List<Map<String, Object>> tables = new JdbcTemplate(proprietaire).queryForList("""
                SELECT c.relname AS nom, c.relrowsecurity AS rls,
                       (SELECT count(*) FROM pg_policies p WHERE p.tablename = c.relname) AS politiques
                FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid
                WHERE a.attname = 'tenant_id' AND c.relkind = 'r' AND c.relnamespace = 'public'::regnamespace""");
        assertFalse(tables.isEmpty(), "au moins une table est isolée");
        for (Map<String, Object> t : tables) {
            assertEquals(Boolean.TRUE, t.get("rls"), t.get("nom") + " : RLS inactive");
            assertTrue(((Number) t.get("politiques")).longValue() > 0, t.get("nom") + " : aucune politique");
        }
    }

    @Test
    void sans_organisation_aucune_ligne_propre_a_une_organisation_n_est_lisible() {
        String code = codeUnique("sans-contexte");
        sql.enregistrer(type(code, valeur("commune", 1, true)));
        sql.ajouterValeurOrganisation(code, A.valeur(), valeur("propre", 2, true));
        JdbcTemplate proprio = new JdbcTemplate(proprietaire);
        JdbcTemplate app = new JdbcTemplate(application);
        // Une table en ajout seul (sans SELECT pour l'application) est encore plus fermée : elle n'a rien à éprouver ici.
        List<String> tables = proprio.queryForList("""
                SELECT c.relname FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid
                WHERE a.attname = 'tenant_id' AND c.relkind = 'r' AND c.relnamespace = 'public'::regnamespace
                  AND has_table_privilege(?, c.oid, 'SELECT')""", String.class, proprietes.roleApplicatif());
        long vuesParLeProprietaire = 0;
        for (String table : tables) {
            String requete = "SELECT count(*) FROM \"" + table + "\" WHERE tenant_id IS NOT NULL";
            vuesParLeProprietaire += proprio.queryForObject(requete, Long.class);
            Long vuesSansContexte = app.queryForObject(requete, Long.class);
            assertEquals(0L, vuesSansContexte == null ? -1L : vuesSansContexte, table + " : lignes d'organisation lisibles sans contexte");
        }
        assertTrue(vuesParLeProprietaire > 0, "le contrôle porte sur des lignes qui existent");
    }

    @Test
    void le_role_applicatif_ne_contourne_pas_la_rls_et_ne_possede_aucune_table() {
        Map<String, Object> role = new JdbcTemplate(proprietaire).queryForMap(
                "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = ?", proprietes.roleApplicatif());
        assertEquals(Boolean.FALSE, role.get("rolsuper"));
        assertEquals(Boolean.FALSE, role.get("rolbypassrls"));
        Long possedees = new JdbcTemplate(proprietaire).queryForObject(
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tableowner = ?", Long.class, proprietes.roleApplicatif());
        assertEquals(0L, possedees);
        assertEquals(proprietes.roleApplicatif(), new JdbcTemplate(application).queryForObject("SELECT current_user", String.class));
    }

    @Test
    void chaque_organisation_ne_voit_que_les_valeurs_communes_et_les_siennes() {
        String code = codeUnique("isolation");
        sql.enregistrer(type(code, valeur("commune", 1, true), valeur("surchargee", 2, true)));
        sql.ajouterValeurOrganisation(code, A.valeur(), valeur("propre-a", 3, true));
        sql.ajouterValeurOrganisation(code, A.valeur(), valeur("surchargee", 9, true));
        sql.ajouterValeurOrganisation(code, B.valeur(), valeur("propre-b", 3, true));

        assertEquals(List.of("commune", "surchargee"), codes(code), "sans organisation : valeurs communes seulement");
        organisation.agirPour(A);
        assertEquals(List.of("commune", "propre-a", "surchargee"), codes(code));
        assertEquals(9, valeurs(code).stream().filter(v -> v.code().valeur().equals("surchargee")).findFirst().orElseThrow().ordre(),
                "la valeur de l'organisation remplace la valeur commune");
        organisation.agirPour(B);
        assertEquals(List.of("commune", "propre-b", "surchargee"), codes(code));
    }

    @Test
    void une_connexion_rendue_au_pool_ne_garde_pas_l_organisation_precedente() {
        JdbcTemplate jdbc = new JdbcTemplate(application);
        organisation.agirPour(A);
        assertEquals(A.valeur().toString(), jdbc.queryForObject("SELECT current_setting('app.organisation')", String.class));
        organisation.anonyme();
        for (int i = 0; i < 20; i++) {
            assertEquals("", jdbc.queryForObject("SELECT current_setting('app.organisation')", String.class));
        }
    }

    @Test
    void l_application_ne_peut_ni_ecrire_un_referentiel_ni_se_soustraire_a_son_role() {
        JdbcTemplate jdbc = new JdbcTemplate(application);
        organisation.agirPour(A);
        assertThrows(BadSqlGrammarException.class, () -> jdbc.update(
                "INSERT INTO referentiel_type (code, libelles) VALUES ('intrusion', '{}'::jsonb)"));
        String proprio = new JdbcTemplate(proprietaire).queryForObject("SELECT current_user", String.class);
        org.springframework.dao.DataAccessException e = assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.execute("SET ROLE " + proprio));
        assertEquals("42501", ((java.sql.SQLException) e.getMostSpecificCause()).getSQLState(),
                "le compte applicatif ne peut pas redevenir propriétaire (privilège insuffisant) : " + e.getMostSpecificCause().getMessage());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.execute("SET ROLE postgres"));
        assertEquals(proprietes.roleApplicatif(), jdbc.queryForObject("SELECT session_user", String.class),
                "la session elle-même appartient au compte applicatif : un RESET ROLE n'y change rien");
    }

    private List<String> codes(String type) {
        return valeurs(type).stream().map(v -> v.code().valeur()).sorted().toList();
    }

    private List<ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel> valeurs(String type) {
        TypeReferentiel t = referentiels.type(new CodeReferentiel(type)).orElseThrow();
        assertNotNull(t.libelles().pour(new CodeLangue("fr"), new CodeLangue("fr"), type));
        return t.valeursActives();
    }
}
