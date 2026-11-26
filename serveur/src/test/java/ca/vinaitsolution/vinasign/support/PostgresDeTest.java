package ca.vinaitsolution.vinasign.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL réel pour les tests d'intégration (jamais H2 ni double en mémoire de la base), partagé entre contextes.
 * Le compte applicatif y est provisionné comme en production : LOGIN, sans privilège, mot de passe aléatoire propre à
 * l'exécution.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDeTest {

    public static final String ROLE_APPLICATIF = "vinasign_application";
    private static final String MOT_DE_PASSE = UUID.randomUUID().toString();
    private static final String CLE_CODES = java.util.Base64.getEncoder().encodeToString(
            java.nio.ByteBuffer.allocate(32).putLong(java.util.concurrent.ThreadLocalRandom.current().nextLong())
                    .putLong(System.nanoTime()).putLong(java.util.concurrent.ThreadLocalRandom.current().nextLong()).array());
    private static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:16-alpine").withCommand("postgres", "-c", "max_connections=400");

    static {
        BASE.start();
        try (Connection c = DriverManager.getConnection(BASE.getJdbcUrl(), BASE.getUsername(), BASE.getPassword());
             Statement s = c.createStatement()) {
            s.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + ROLE_APPLICATIF + "') THEN "
                    + "CREATE ROLE " + ROLE_APPLICATIF + " LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB PASSWORD '"
                    + MOT_DE_PASSE + "'; END IF; END $$");
        } catch (SQLException e) {
            throw new IllegalStateException("Provisionnement du compte applicatif impossible.", e);
        }
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return BASE;
    }

    @Bean
    DynamicPropertyRegistrar compteApplicatif() {
        return registre -> {
            registre.add("vinasign.base.role-applicatif", () -> ROLE_APPLICATIF);
            registre.add("vinasign.base.mot-de-passe-applicatif", () -> MOT_DE_PASSE);
            // Clé HMAC des codes à usage unique, propre à l'exécution : aucune clé dans le dépôt.
            registre.add("vinasign.session.cle-codes", () -> CLE_CODES);
            registre.add("vinasign.chiffrement.cle", () -> CLE_CODES);
            registre.add("vinasign.notification.fournisseur-sms", () -> "simulateur");
        };
    }
}
