package ca.vinaitsolution.vinasign.referentiel.infrastructure.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.support.PostgresDeTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Une lecture de référentiel n'emprunte qu'une connexion à la fois. Lire les valeurs pendant que le résultat des types
 * tenait encore sa connexion en exigeait deux : sous une rafale de requêtes (l'écran « Référentiels », l'habilitation de
 * chaque requête authentifiée), toutes tenaient la première et attendaient la seconde, et le pool se bloquait jusqu'à
 * l'expiration de l'attente. Un pool d'une seule connexion rend ce défaut immédiatement visible.
 */
@SpringBootTest
@Import(PostgresDeTest.class)
class ReferentielsJdbcConnexionUniqueTest {

    @Autowired HikariDataSource poolApplicatif;

    @Test
    void types_et_type_se_lisent_avec_une_seule_connexion() {
        try (HikariDataSource unique = new HikariDataSource()) {
            unique.setJdbcUrl(poolApplicatif.getJdbcUrl());
            unique.setUsername(poolApplicatif.getUsername());
            unique.setPassword(poolApplicatif.getPassword());
            unique.setMaximumPoolSize(1);
            unique.setConnectionTimeout(500);
            ReferentielsJdbc adaptateur = new ReferentielsJdbc(new NamedParameterJdbcTemplate(unique), new ObjectMapper());

            List<TypeReferentiel> types = adaptateur.types();
            assertFalse(types.isEmpty());
            assertTrue(types.stream().anyMatch(t -> !t.valeursActives().isEmpty()), "les valeurs sont lues avec leurs types");
            TypeReferentiel langue = adaptateur.type(new CodeReferentiel("langue")).orElseThrow();
            assertFalse(langue.valeursActives().isEmpty());
            assertTrue(adaptateur.type(new CodeReferentiel("inexistant-" + System.nanoTime())).isEmpty());
        }
    }
}
