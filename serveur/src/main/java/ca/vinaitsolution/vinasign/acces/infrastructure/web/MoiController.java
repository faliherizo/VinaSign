package ca.vinaitsolution.vinasign.acces.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.OrganisationCourante;
import ca.vinaitsolution.vinasign.commun.domain.IdentifiantOrganisation;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ce que le portail affiche et autorise pour la personne connectée : son organisation et ses rôles accordés (VS-014). */
@RestController
public class MoiController {

    /** @param roles rôles effectivement accordés pour cette session (MFA comprise) */
    public record Moi(String acteur, UUID organisation, List<String> roles) {
    }

    private final ActeurCourant acteur;
    private final OrganisationCourante organisation;

    public MoiController(ActeurCourant acteur, OrganisationCourante organisation) {
        this.acteur = acteur;
        this.organisation = organisation;
    }

    @GetMapping("/api/v1/moi")
    public Moi moi(Authentication authentification) {
        List<String> roles = authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring("ROLE_".length())).sorted().toList();
        return new Moi(acteur.courant().reference(), organisation.courante().map(IdentifiantOrganisation::valeur).orElse(null), roles);
    }
}
