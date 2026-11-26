package ca.vinaitsolution.vinasign.enveloppe.infrastructure.web;

import ca.vinaitsolution.vinasign.enveloppe.application.ListerEnveloppes;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liste des enveloppes de l'appelant (VS-064) : l'application les siennes, le préparateur et l'auditeur celles du portail
 * de leur organisation. {@code statut} filtre par un code du référentiel statut-enveloppe ; {@code apres} reprend au
 * curseur rendu par la page précédente.
 */
@RestController
public class ListeEnveloppesController {

    private final ListerEnveloppes lister;

    public ListeEnveloppesController(ListerEnveloppes lister) {
        this.lister = lister;
    }

    @GetMapping("/api/v1/envelopes")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_enveloppes-lire') or hasAnyRole('preparateur', 'auditeur')")
    public ListerEnveloppes.Page lister(@RequestParam(required = false) String statut,
                                       @RequestParam(required = false) String apres,
                                       @RequestParam(defaultValue = "25") int limite) {
        return lister.executer(Optional.ofNullable(statut), Optional.ofNullable(apres), limite);
    }
}
