package ca.vinaitsolution.vinasign.commun.infrastructure.base;

import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Deux comptes sur la même base (VS-005) :
 * <ul>
 *   <li>les migrations (Liquibase) avec le compte propriétaire du schéma ;</li>
 *   <li>l'application avec le compte applicatif, soumis à la RLS ; l'organisation courante est posée à chaque emprunt.</li>
 * </ul>
 * Se connecter directement sous le compte applicatif, plutôt que d'endosser son rôle depuis le propriétaire, ferme la
 * voie d'un {@code RESET ROLE} par injection SQL. Adresse et identifiants viennent de l'environnement, jamais du code.
 */
@Configuration
public class BaseConfiguration {

    @Bean
    @ConfigurationProperties("vinasign.base.pool")
    HikariDataSource poolApplicatif(JdbcConnectionDetails connexion, BaseProprietes proprietes) {
        return pool("vinasign-application", connexion.getJdbcUrl(), proprietes.roleApplicatif(), proprietes.motDePasseApplicatif(),
                connexion.getDriverClassName());
    }

    @Bean
    @LiquibaseDataSource
    DataSource sourceMigration(JdbcConnectionDetails connexion) {
        HikariDataSource migration = pool("vinasign-migration", connexion.getJdbcUrl(), connexion.getUsername(), connexion.getPassword(),
                connexion.getDriverClassName());
        migration.setMaximumPoolSize(2);
        return migration;
    }

    /** Source de toute requête applicative (JdbcTemplate, transactions) : organisation posée à chaque emprunt. */
    @Bean
    @Primary
    SourceDonneesOrganisation sourceDonnees(HikariDataSource poolApplicatif, OrganisationCourante organisation) {
        return new SourceDonneesOrganisation(poolApplicatif, organisation);
    }

    private static HikariDataSource pool(String nom, String url, String utilisateur, String motDePasse, String pilote) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(nom);
        ds.setJdbcUrl(url);
        ds.setUsername(utilisateur);
        ds.setPassword(motDePasse);
        ds.setDriverClassName(pilote);
        return ds;
    }
}
