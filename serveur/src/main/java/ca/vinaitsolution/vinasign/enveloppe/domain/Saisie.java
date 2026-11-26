package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saisie d'un signataire : valeurs de ses champs. Un champ obligatoire vide bloque la signature ; une valeur pour un
 * champ qui n'est pas le sien est refusée. Les zones remplies par l'acte de signer (signature, initiales) ne demandent
 * aucune valeur : la signature elle-même, dessinée ou tapée, les remplit.
 */
public final class Saisie {

    private Saisie() {
    }

    public static void verifier(UUID signataire, List<Champ> champs, Map<UUID, String> valeurs) {
        verifier(signataire, champs, valeurs, Set.of());
    }

    /** @param rempliesParLActe champs remplis par l'acte de signer (type dont la saisie est « signature ») */
    public static void verifier(UUID signataire, List<Champ> champs, Map<UUID, String> valeurs, Set<UUID> rempliesParLActe) {
        List<UUID> siens = champs.stream().filter(c -> c.signataire().equals(signataire)).map(Champ::id).toList();
        if (!siens.containsAll(valeurs.keySet())) {
            throw new SaisieInvalide("champ-etranger", "Une valeur porte sur un champ qui n'est pas assigné à ce signataire.");
        }
        List<String> manquants = champs.stream().filter(c -> c.signataire().equals(signataire) && c.obligatoire() && !rempliesParLActe.contains(c.id()))
                .filter(c -> valeurs.getOrDefault(c.id(), "").isBlank()).map(Champ::libelle).toList();
        if (!manquants.isEmpty()) {
            throw new ChampsIncomplets(manquants);
        }
    }
}
