package ca.vinaitsolution.vinasign.support;

import ca.vinaitsolution.vinasign.acces.application.port.ApplicationsClientes;
import ca.vinaitsolution.vinasign.acces.application.port.RegistreApplications;
import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.CleDejaEnregistree;
import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Double des ports {@link ApplicationsClientes} et {@link RegistreApplications}, soumis au même contrat que l'adaptateur JDBC (RLS comprise). */
public class ApplicationsClientesEnMemoire implements ApplicationsClientes, RegistreApplications {

    private final OrganisationCourante organisation;
    private final List<ApplicationCliente> applications = new ArrayList<>();
    private final Map<String, IdentifiantClient> empreintes = new HashMap<>();

    public ApplicationsClientesEnMemoire(OrganisationCourante organisation) {
        this.organisation = organisation;
    }

    @Override
    public synchronized Optional<ApplicationCliente> activeParClient(IdentifiantClient client) {
        return applications.stream().filter(a -> a.client().equals(client) && a.active()).findFirst();
    }

    @Override
    public synchronized void ajouter(ApplicationCliente a, ClesPubliques cles) {
        if (cles.empreintes().stream().anyMatch(empreintes::containsKey)) {
            throw new CleDejaEnregistree();
        }
        if (!organisation.courante().map(a.organisation()::equals).orElse(false)) {
            throw new IllegalStateException("Écriture hors de l'organisation courante.");
        }
        applications.add(a);
        cles.empreintes().forEach(e -> empreintes.put(e, a.client()));
    }

    @Override
    public synchronized List<ApplicationCliente> deLOrganisationCourante() {
        return applications.stream().filter(a -> organisation.courante().map(a.organisation()::equals).orElse(false))
                .sorted(Comparator.comparing(ApplicationCliente::nom).thenComparing(a -> a.client().valeur())).toList();
    }

    @Override
    public synchronized Optional<ApplicationCliente> deLOrganisation(IdentifiantClient client) {
        return deLOrganisationCourante().stream().filter(a -> a.client().equals(client)).findFirst();
    }

    @Override
    public synchronized void remplacer(ApplicationCliente a) {
        applications.replaceAll(x -> x.client().equals(a.client()) ? a : x);
    }

    @Override
    public synchronized void remplacerCles(ApplicationCliente a, ClesPubliques cles) {
        if (cles.empreintes().stream().anyMatch(e -> empreintes.containsKey(e) && !empreintes.get(e).equals(a.client()))) {
            throw new CleDejaEnregistree();
        }
        empreintes.values().removeIf(a.client()::equals);
        cles.empreintes().forEach(e -> empreintes.put(e, a.client()));
        remplacer(a);
    }
}
