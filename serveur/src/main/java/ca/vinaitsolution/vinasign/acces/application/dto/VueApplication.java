package ca.vinaitsolution.vinasign.acces.application.dto;

import ca.vinaitsolution.vinasign.acces.domain.ApplicationCliente;
import ca.vinaitsolution.vinasign.acces.domain.OrigineIntegration;
import java.util.List;

/** Application cliente telle que l'administration la voit : jamais de clé, seulement ce qui la décrit. */
public record VueApplication(String clientId, String nom, String environnement, List<String> portees, List<String> origines,
                             String statut) {

    public static VueApplication de(ApplicationCliente a) {
        return new VueApplication(a.client().valeur(), a.nom(), a.environnement(), a.portees().stream().sorted().toList(),
                a.origines().stream().map(OrigineIntegration::valeur).sorted().toList(), a.active() ? "active" : "revoquee");
    }
}
