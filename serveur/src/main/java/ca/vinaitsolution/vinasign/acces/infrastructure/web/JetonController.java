package ca.vinaitsolution.vinasign.acces.infrastructure.web;

import ca.vinaitsolution.vinasign.acces.application.dto.VueJeton;
import ca.vinaitsolution.vinasign.commun.infrastructure.securite.OrganisationCouranteJeton;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Jeton courant (VS-012) : application, organisation, portées accordées et échéance. */
@RestController
@RequestMapping("/api/v1/jeton")
public class JetonController {

    @GetMapping
    public VueJeton jeton(@AuthenticationPrincipal Jwt jeton) {
        List<String> portees = jeton.getClaimAsStringList("scope");
        return new VueJeton(jeton.getSubject(), jeton.getClaimAsString(OrganisationCouranteJeton.REVENDICATION),
                portees == null ? List.of() : portees.stream().sorted().toList(), jeton.getExpiresAt());
    }
}
