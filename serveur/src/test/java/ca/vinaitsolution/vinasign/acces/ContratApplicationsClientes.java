package ca.vinaitsolution.vinasign.acces;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.application.port.RegistreApplications;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.CleDejaEnregistree;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.acces.domain.OrigineIntegration;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Contrat des ports {@link ApplicationsClientes} et {@link RegistreApplications}, exécuté contre chaque implémentation (LSP), erreurs comprises. */
public abstract class ContratApplicationsClientes {

    protected abstract ApplicationsClientes applications();

    protected abstract RegistreApplications registre();

    /** Pose l'organisation courante (null : anonyme) et garantit son existence. */
    protected abstract void agirPour(IdentifiantOrganisation organisation);

    @AfterEach
    void anonyme() {
        agirPour(null);
    }

    private static ApplicationCliente application(IdentifiantOrganisation org, String nom) {
        return ApplicationCliente.enregistrer(UUID.randomUUID(), org, new IdentifiantClient("vs-" + UUID.randomUUID()), nom,
                "bac-a-sable", cles(), Set.of("enveloppes-lire"), Set.of(new OrigineIntegration("https://app.exemple.ca")));
    }

    private static ClesPubliques cles() {
        // Forme canonique de jsonb : la base reformate le JSON, la relecture doit rester égale.
        return new ClesPubliques("{\"keys\": []}", Set.of(UUID.randomUUID().toString().replace("-", "")));
    }

    @Test
    void une_application_ajoutee_est_relue_a_l_identique_par_son_client() {
        IdentifiantOrganisation org = new IdentifiantOrganisation(UUID.randomUUID());
        agirPour(org);
        ApplicationCliente a = application(org, "Système RH");
        registre().ajouter(a, cles());
        agirPour(null);
        assertEquals(a, applications().activeParClient(a.client()).orElseThrow(), "lisible au point de jeton, sans organisation");
    }

    @Test
    void la_liste_ne_rend_que_l_organisation_courante_triee_par_nom() {
        IdentifiantOrganisation a = new IdentifiantOrganisation(UUID.randomUUID());
        IdentifiantOrganisation b = new IdentifiantOrganisation(UUID.randomUUID());
        agirPour(a);
        ApplicationCliente zeta = application(a, "Zêta");
        ApplicationCliente alpha = application(a, "Alpha");
        registre().ajouter(zeta, cles());
        registre().ajouter(alpha, cles());
        agirPour(b);
        registre().ajouter(application(b, "Autre"), cles());
        agirPour(a);
        assertEquals(java.util.List.of(alpha, zeta), registre().deLOrganisationCourante());
    }

    @Test
    void une_cle_deja_enregistree_est_refusee_meme_pour_une_autre_organisation() {
        ClesPubliques partagees = cles();
        IdentifiantOrganisation a = new IdentifiantOrganisation(UUID.randomUUID());
        agirPour(a);
        registre().ajouter(application(a, "Première"), partagees);
        IdentifiantOrganisation b = new IdentifiantOrganisation(UUID.randomUUID());
        agirPour(b);
        assertThrows(CleDejaEnregistree.class, () -> registre().ajouter(application(b, "Seconde"), partagees));
    }

    @Test
    void un_client_inconnu_rend_vide() {
        assertTrue(applications().activeParClient(new IdentifiantClient("vs-inconnu-123")).isEmpty());
    }

    @Test
    void une_application_se_relit_dans_son_organisation_seulement() {
        IdentifiantOrganisation a = new IdentifiantOrganisation(UUID.randomUUID());
        agirPour(a);
        ApplicationCliente app = application(a, "Propre");
        registre().ajouter(app, cles());
        assertEquals(app, registre().deLOrganisation(app.client()).orElseThrow());
        agirPour(new IdentifiantOrganisation(UUID.randomUUID()));
        assertTrue(registre().deLOrganisation(app.client()).isEmpty(), "invisible d'une autre organisation");
    }

    @Test
    void une_application_revoquee_n_est_plus_active_au_point_de_jeton() {
        IdentifiantOrganisation a = new IdentifiantOrganisation(UUID.randomUUID());
        agirPour(a);
        ApplicationCliente app = application(a, "Révocable");
        registre().ajouter(app, cles());
        ApplicationCliente revoquee = app.revoquer(java.time.Instant.parse("2026-11-22T17:00:00Z"));
        registre().remplacer(revoquee);
        assertEquals(revoquee, registre().deLOrganisation(app.client()).orElseThrow());
        assertTrue(applications().activeParClient(app.client()).isEmpty());
    }

    @Test
    void une_rotation_remplace_les_cles_et_libere_les_anciennes_empreintes() {
        IdentifiantOrganisation a = new IdentifiantOrganisation(UUID.randomUUID());
        agirPour(a);
        ClesPubliques anciennes = cles();
        ApplicationCliente app = application(a, "Rotation");
        registre().ajouter(app, anciennes);
        ClesPubliques nouvelles = new ClesPubliques("{\"keys\": [{}]}", Set.of("nouvelle-" + UUID.randomUUID()));
        ApplicationCliente apres = app.remplacerCles(nouvelles);
        registre().remplacerCles(apres, nouvelles);
        assertEquals(nouvelles.jwks(), registre().deLOrganisation(app.client()).orElseThrow().clesPubliques());
        registre().ajouter(application(a, "Reprend l'ancienne"), anciennes);
        ApplicationCliente autre = application(a, "Voleuse");
        registre().ajouter(autre, cles());
        assertThrows(CleDejaEnregistree.class, () -> registre().remplacerCles(autre.remplacerCles(nouvelles), nouvelles));
    }
}
