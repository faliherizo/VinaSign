package ca.vinaitsolution.vinasign.acces.application;

import ca.vinaitsolution.vinasign.acces.application.port.AnnuaireConnexion;
import ca.vinaitsolution.vinasign.acces.domain.Habilitation;
import ca.vinaitsolution.vinasign.acces.domain.IdentiteExterne;
import ca.vinaitsolution.vinasign.acces.domain.UtilisateurPortail;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.TypeReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.ValeurReferentiel;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Habilitation d'un utilisateur du portail à chaque requête (VS-014). Un rôle marqué {@code exigeMfa} dans le
 * référentiel {@code role-portail} n'est accordé que si le fournisseur atteste une authentification multifacteur ; un rôle
 * retiré du référentiel n'est plus accordé. Un utilisateur inconnu ou désactivé n'a aucune habilitation.
 */
public class AuthentifierUtilisateur {

    static final CodeReferentiel ROLES = new CodeReferentiel("role-portail");
    static final String EXIGE_MFA = "exigeMfa";

    private final AnnuaireConnexion annuaire;
    private final Referentiels referentiels;
    private final Set<String> methodesMultifacteur;

    /** @param methodesMultifacteur valeurs {@code amr} reconnues comme multifacteur (configuration) */
    public AuthentifierUtilisateur(AnnuaireConnexion annuaire, Referentiels referentiels, Set<String> methodesMultifacteur) {
        this.annuaire = annuaire;
        this.referentiels = referentiels;
        this.methodesMultifacteur = Set.copyOf(methodesMultifacteur);
    }

    public Optional<Habilitation> executer(IdentiteExterne identite) {
        return annuaire.authentifier(identite).filter(UtilisateurPortail::actif)
                .map(u -> new Habilitation(u.id(), u.organisation(), rolesAccordes(u, identite)));
    }

    private Set<String> rolesAccordes(UtilisateurPortail u, IdentiteExterne identite) {
        boolean multifacteur = !Collections.disjoint(identite.methodes(), methodesMultifacteur);
        TypeReferentiel roles = referentiels.type(ROLES).orElseThrow(() -> new IllegalStateException("Référentiel role-portail absent."));
        return u.roles().stream()
                .map(code -> roles.valeurActive(new CodeReferentiel(code)))
                .flatMap(Optional::stream)
                .filter(r -> multifacteur || !"true".equals(r.attributs().get(EXIGE_MFA)))
                .map(ValeurReferentiel::code).map(CodeReferentiel::valeur)
                .collect(Collectors.toSet());
    }
}
