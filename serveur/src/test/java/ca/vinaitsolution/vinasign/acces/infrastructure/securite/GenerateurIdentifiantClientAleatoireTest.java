package ca.vinaitsolution.vinasign.acces.infrastructure.securite;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GenerateurIdentifiantClientAleatoireTest {

    @Test
    void chaque_identifiant_porte_le_prefixe_et_128_bits_aleatoires_sans_repetition() {
        GenerateurIdentifiantClientAleatoire generateur = new GenerateurIdentifiantClientAleatoire(new SecureRandom());
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String id = generateur.suivant().valeur();
            assertTrue(id.matches("vs-[A-Za-z0-9_-]{22}"), id);
            assertTrue(vus.add(id));
        }
    }
}
