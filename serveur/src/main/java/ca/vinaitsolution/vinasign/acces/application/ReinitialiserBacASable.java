package ca.vinaitsolution.vinasign.acces.application;

import ca.vinaitsolution.vinasign.acces.application.port.BacsASable;
import ca.vinaitsolution.vinasign.acces.domain.BacASableRefuse;
import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import java.time.Clock;
import java.util.UUID;

/**
 * Réinitialisation du bac à sable d'une application (VS-095) : elle repart d'une organisation vide, au même plan. Rien n'est
 * effacé : le journal de preuve reste en ajout seul. Permise seulement sur une plateforme déployée en bac à sable, et pour une
 * application d'un environnement réinitialisable. Le jeton suivant de l'application porte la nouvelle organisation.
 */
public class ReinitialiserBacASable {

    private final BacsASable bacs;
    private final ContexteAccesCourant acces;
    private final GenerateurIdentifiant identifiants;
    private final Clock horloge;
    private final boolean permise;

    public ReinitialiserBacASable(BacsASable bacs, ContexteAccesCourant acces, GenerateurIdentifiant identifiants, Clock horloge, boolean permise) {
        this.bacs = bacs;
        this.acces = acces;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.permise = permise;
    }

    public UUID executer() {
        if (!permise) {
            throw new BacASableRefuse("Cette plateforme n'est pas un bac à sable : aucune donnée n'y est réinitialisable.");
        }
        String client = acces.courant().client().orElseThrow(() -> new BacASableRefuse("Seule une application cliente réinitialise son bac à sable."));
        return bacs.reinitialiser(new IdentifiantClient(client), identifiants.suivant(), horloge.instant())
                .orElseThrow(() -> new BacASableRefuse("Seule une application de bac à sable se réinitialise."));
    }
}
