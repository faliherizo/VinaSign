package ca.vinaitsolution.vinasign.etat.infrastructure.web;

import ca.vinaitsolution.vinasign.etat.application.ConsulterEtat;
import ca.vinaitsolution.vinasign.etat.application.dto.VueEtat;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * État du service (VS-124), public : lu par la page d'état et par les sondes synthétiques. Aucune donnée d'organisation ;
 * mis en cache une minute pour absorber un afflux pendant un incident.
 */
@RestController
public class EtatController {

    private final ConsulterEtat etat;

    public EtatController(ConsulterEtat etat) {
        this.etat = etat;
    }

    @GetMapping("/api/v1/availability")
    @Transactional(readOnly = true)
    public ResponseEntity<VueEtat> consulter() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic()).body(etat.consulter());
    }
}
