package ca.vinaitsolution.vinasign.support;

import ca.vinaitsolution.vinasign.commun.application.port.AbonneEvenements;
import ca.vinaitsolution.vinasign.commun.domain.EvenementMetier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestComponent;

/** Capte les invitations comme le ferait le contexte des notifications : le test reçoit le jeton du lien comme le signataire. */
@TestComponent
public class InvitationsCapturees implements AbonneEvenements {

    private final List<Map<String, String>> recues = new CopyOnWriteArrayList<>();

    @Override
    public boolean concerne(String type) {
        return "invitation-a-envoyer".equals(type);
    }

    @Override
    public void recevoir(EvenementMetier evenement) {
        recues.add(evenement.attributs());
    }

    public List<Map<String, String>> pour(String enveloppe) {
        return recues.stream().filter(a -> enveloppe.equals(a.get("enveloppe"))).toList();
    }
}
