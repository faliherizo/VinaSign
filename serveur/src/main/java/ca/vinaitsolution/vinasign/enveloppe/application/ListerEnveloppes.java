package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.enveloppe.application.port.ListeEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ListeEnveloppes.Position;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ListeEnveloppes.Resume;
import ca.vinaitsolution.vinasign.enveloppe.domain.SaisieInvalide;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Liste des enveloppes de l'appelant (VS-064) : une application voit les siennes, un utilisateur du portail celles de
 * l'espace portail de son organisation. Pagination par curseur opaque, stable quand des enveloppes s'ajoutent.
 */
public class ListerEnveloppes {

    public static final int LIMITE_MAX = 100;
    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("[a-z-]{1,30}");
    private static final int CURSEUR_MAX = 200;

    /** Une page et, s'il reste des enveloppes, le curseur de la suivante. */
    public record Page(List<Resume> elements, Optional<String> suivant) {
    }

    private final ServicesEnveloppe services;
    private final ListeEnveloppes liste;

    public ListerEnveloppes(ServicesEnveloppe services, ListeEnveloppes liste) {
        this.services = services;
        this.liste = liste;
    }

    public Page executer(Optional<String> statut, Optional<String> curseur, int limite) {
        if (limite < 1 || limite > LIMITE_MAX) {
            throw new SaisieInvalide("limite-invalide", "La limite va de 1 à " + LIMITE_MAX + ".");
        }
        if (statut.isPresent() && !CODE.matcher(statut.get()).matches()) {
            throw new SaisieInvalide("statut-invalide", "Le statut est un code du référentiel statut-enveloppe.");
        }
        String application = services.appelant().application();
        List<Resume> lues = liste.de(application, statut, curseur.map(ListerEnveloppes::lire), limite + 1);
        if (lues.size() <= limite) {
            return new Page(lues, Optional.empty());
        }
        List<Resume> page = lues.subList(0, limite);
        Resume derniere = page.get(limite - 1);
        return new Page(List.copyOf(page), Optional.of(ecrire(new Position(derniere.creeLe(), derniere.id()))));
    }

    static String ecrire(Position p) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((p.creeLe() + "~" + p.id()).getBytes(StandardCharsets.UTF_8));
    }

    static Position lire(String curseur) {
        try {
            if (curseur.length() > CURSEUR_MAX) {
                throw new IllegalArgumentException("curseur trop long");
            }
            String[] parties = new String(Base64.getUrlDecoder().decode(curseur), StandardCharsets.UTF_8).split("~", 2);
            return new Position(Instant.parse(parties[0]), UUID.fromString(parties[1]));
        } catch (RuntimeException e) {
            throw new SaisieInvalide("curseur-invalide", "Curseur de pagination invalide : reprendre la liste depuis le début.");
        }
    }
}
