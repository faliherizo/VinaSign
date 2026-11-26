package ca.vinaitsolution.vinasign.acces.infrastructure.web;

import ca.vinaitsolution.vinasign.acces.application.ReinitialiserBacASable;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Réinitialisation du bac à sable de l'application appelante (VS-095). Demander ensuite un nouveau jeton. */
@RestController
public class BacASableController {

    private final ReinitialiserBacASable reinitialisation;

    public BacASableController(ReinitialiserBacASable reinitialisation) {
        this.reinitialisation = reinitialisation;
    }

    @PostMapping("/api/v1/sandbox/reset")
    @Transactional
    public Map<String, UUID> reinitialiser() {
        return Map.of("organisation", reinitialisation.executer());
    }
}
