package ca.vinaitsolution.vinasign.acces.domain;

import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Application autorisée à appeler l'API pour une organisation (VS-011, VS-012). Elle s'authentifie par une assertion
 * signée de sa clé privée (private_key_jwt) : VinaSign ne détient que ses clés publiques.
 *
 * @param environnement code du référentiel {@code environnement-application}
 * @param portees       codes du référentiel {@code portee-api} accordés
 * @param clesPubliques jeu de clés publiques (JWKS, JSON), validé à l'enregistrement
 * @param origines      origines autorisées à intégrer l'interface du signataire
 */
public record ApplicationCliente(UUID id, IdentifiantOrganisation organisation, IdentifiantClient client, String nom,
                                 String environnement, String clesPubliques, Set<String> portees,
                                 Set<OrigineIntegration> origines, StatutApplication statut, Instant revoqueLe) {

    public ApplicationCliente {
        if (id == null || organisation == null || client == null || statut == null) {
            throw new IllegalArgumentException("Application cliente incomplète.");
        }
        if (nom == null || nom.isBlank() || nom.strip().length() > 120 || clesPubliques == null || clesPubliques.isBlank()) {
            throw new IllegalArgumentException("Une application cliente a un nom (120 caractères au plus) et des clés publiques.");
        }
        if (environnement == null || environnement.isBlank()) {
            throw new IllegalArgumentException("Une application cliente appartient à un environnement.");
        }
        nom = nom.strip();
        portees = portees == null ? Set.of() : Set.copyOf(portees);
        origines = origines == null ? Set.of() : Set.copyOf(origines);
    }

    /** Nouvelle application, active. */
    public static ApplicationCliente enregistrer(UUID id, IdentifiantOrganisation organisation, IdentifiantClient client, String nom,
                                                 String environnement, ClesPubliques cles, Set<String> portees,
                                                 Set<OrigineIntegration> origines) {
        return new ApplicationCliente(id, organisation, client, nom, environnement, cles.jwks(), portees, origines,
                StatutApplication.ACTIVE, null);
    }

    public boolean active() {
        return statut == StatutApplication.ACTIVE;
    }

    /** Révocation définitive : les jetons de cette application sont refusés dès la vérification suivante (VS-013). */
    public ApplicationCliente revoquer(Instant le) {
        exigerActive("Application déjà révoquée.");
        return new ApplicationCliente(id, organisation, client, nom, environnement, clesPubliques, portees, origines, StatutApplication.REVOQUEE,
                le);
    }

    /**
     * Rotation des clés (VS-013) : le nouveau jeu remplace l'ancien. Pendant une rotation, il contient l'ancienne et la
     * nouvelle clé, toutes deux acceptées ; une seconde rotation retire l'ancienne. Une application révoquée ne change plus.
     */
    public ApplicationCliente remplacerCles(ClesPubliques cles) {
        exigerActive("Une application révoquée ne change plus de clés.");
        return new ApplicationCliente(id, organisation, client, nom, environnement, cles.jwks(), portees, origines, statut, revoqueLe);
    }

    private void exigerActive(String motif) {
        if (!active()) {
            throw new TransitionApplicationInterdite(motif);
        }
    }
}
