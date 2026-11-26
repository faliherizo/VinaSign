package ca.vinaitsolution.vinasign.acces.application.dto;

import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import java.util.List;
import java.util.UUID;

/** Utilisateur vu par l'administration de son organisation. */
public record VueUtilisateur(UUID id, String courriel, List<String> roles, boolean actif) {

    public static VueUtilisateur de(UtilisateurPortail u) {
        return new VueUtilisateur(u.id(), u.courriel().valeur(), u.roles().stream().sorted().toList(), u.actif());
    }
}
