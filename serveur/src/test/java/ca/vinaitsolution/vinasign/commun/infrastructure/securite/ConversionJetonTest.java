package ca.vinaitsolution.vinasign.commun.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ConversionJetonTest {

    @Test
    void portees_et_roles_deviennent_des_autorites_distinctes_et_le_sujet_le_nom() {
        Jwt jeton = Jwt.withTokenValue("x").header("alg", "none").subject("utilisateur-1")
                .claim("scope", "enveloppes-lire enveloppes-creer").claim("roles", List.of("administrateur-plateforme"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var auth = ConversionJeton.pourVinaSign().convert(jeton);
        Set<String> autorites = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertEquals(Set.of("SCOPE_enveloppes-lire", "SCOPE_enveloppes-creer", "ROLE_administrateur-plateforme"), autorites);
        assertEquals("utilisateur-1", auth.getName());
    }
}
