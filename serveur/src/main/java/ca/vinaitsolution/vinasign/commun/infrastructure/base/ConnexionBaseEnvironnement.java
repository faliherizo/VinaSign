package ca.vinaitsolution.vinasign.commun.infrastructure.base;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connexion à la base lue dans l'environnement ({@code VINASIGN_DB_URL}, {@code _UTILISATEUR}, {@code _MDP}) : artefact
 * de production, image Docker, campagne de {@code ci.sh}.
 *
 * <p>{@link BaseConfiguration} déclare ses propres pools : l'auto-configuration de Spring Boot s'efface alors et ne
 * publie plus de {@link JdbcConnectionDetails}. Sur un poste, Docker Compose en fournit un ; en test, Testcontainers ;
 * partout ailleurs, sans ce composant, le serveur ne démarrait pas. Il n'est actif que si l'adresse est donnée, pour ne
 * jamais concurrencer les deux autres sources.
 */
@Configuration
@ConditionalOnExpression("!'${spring.datasource.url:}'.isBlank()")
class ConnexionBaseEnvironnement {

    @Bean
    JdbcConnectionDetails connexionEnvironnement(DataSourceProperties proprietes) {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return proprietes.determineUsername();
            }

            @Override
            public String getPassword() {
                return proprietes.determinePassword();
            }

            @Override
            public String getJdbcUrl() {
                return proprietes.determineUrl();
            }
        };
    }
}
