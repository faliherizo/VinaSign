package ca.vinaitsolution.vinasign.signature.caracterisation;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * La référence de caractérisation est la copie exacte du moteur du CLM (VS-ADR-002) : si le contrat change son moteur,
 * ce test le signale, et la copie est remplacée avant d'en tirer une conclusion. Sans le dépôt du contrat sur le poste
 * (CI de VinaSign seule), le test est sans objet.
 */
class CopieFideleClmTest {

    private static final Path CONTRAT = Path.of(System.getProperty("user.home"), "project/contractuelle_contrat/backend/src/main/java");
    private static final Path COPIE = Path.of("src/test/java");
    private static final List<String> CLASSES = List.of(
            "qc/levis/contrat/clm/application/port/CachetElectronique.java",
            "qc/levis/contrat/clm/infrastructure/AutoriteHorodatage.java",
            "qc/levis/contrat/clm/infrastructure/CachetX509.java",
            "qc/levis/contrat/clm/infrastructure/DonneesValidation.java");

    @Test
    void la_copie_est_identique_au_moteur_du_contrat() throws Exception {
        assumeTrue(Files.isDirectory(CONTRAT), "dépôt du contrat absent de ce poste");
        for (String classe : CLASSES) {
            assertEquals(Files.readString(CONTRAT.resolve(classe)), Files.readString(COPIE.resolve(classe)), classe + " a divergé");
        }
    }
}
