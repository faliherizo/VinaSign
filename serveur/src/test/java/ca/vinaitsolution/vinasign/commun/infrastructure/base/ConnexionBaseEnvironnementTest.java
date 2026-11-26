package ca.vinaitsolution.vinasign.commun.infrastructure.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConnexionBaseEnvironnementTest {

    @Configuration
    @EnableConfigurationProperties(DataSourceProperties.class)
    static class Proprietes {
    }

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(Proprietes.class, ConnexionBaseEnvironnement.class);

    @Test
    void lAdresseDonneeParLEnvironnementFournitLaConnexion() {
        contexte.withPropertyValues("spring.datasource.url=jdbc:postgresql://base:5432/vinasign",
                "spring.datasource.username=proprietaire", "spring.datasource.password=secret").run(c -> {
            JdbcConnectionDetails connexion = c.getBean(JdbcConnectionDetails.class);
            assertThat(connexion.getJdbcUrl()).isEqualTo("jdbc:postgresql://base:5432/vinasign");
            assertThat(connexion.getUsername()).isEqualTo("proprietaire");
            assertThat(connexion.getPassword()).isEqualTo("secret");
            assertThat(connexion.getDriverClassName()).isEqualTo("org.postgresql.Driver");
        });
    }

    @Test
    void sansAdresseLaConnexionVientDeDockerComposeOuDeTestcontainers() {
        contexte.withPropertyValues("spring.datasource.url=").run(c -> assertThat(c).doesNotHaveBean(JdbcConnectionDetails.class));
        contexte.run(c -> assertThat(c).doesNotHaveBean(JdbcConnectionDetails.class));
    }
}
