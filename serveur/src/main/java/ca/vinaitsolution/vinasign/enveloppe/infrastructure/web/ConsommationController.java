package ca.vinaitsolution.vinasign.enveloppe.infrastructure.web;

import ca.vinaitsolution.vinasign.enveloppe.application.ReleverConsommation;
import ca.vinaitsolution.vinasign.enveloppe.domain.Consommation;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consommation de l'organisation (VS-120) : relevé JSON, et export CSV (une ligne par mois) pour le rapport mensuel. */
@RestController
@PreAuthorize("hasAuthority('SCOPE_enveloppes-lire')")
public class ConsommationController {

    public record VueReleve(LocalDate du, LocalDate au, int envoyees, int completees, int refusees, int annuleesApresEnvoi, int annuleesAvantEnvoi,
                            int expirees, int renvois, Map<String, Integer> parMois) {
    }

    private final ReleverConsommation releve;

    public ConsommationController(ReleverConsommation releve) {
        this.releve = releve;
    }

    @GetMapping("/api/v1/usage")
    @Transactional(readOnly = true)
    public VueReleve releve(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        Consommation.Releve r = releve.executer(du, au);
        Map<String, Integer> parMois = new LinkedHashMap<>();
        r.parMois().forEach((mois, n) -> parMois.put(mois.toString(), n));
        return new VueReleve(du, au, r.envoyees(), r.completees(), r.refusees(), r.annuleesApresEnvoi(), r.annuleesAvantEnvoi(), r.expirees(),
                r.renvois(), parMois);
    }

    /** Colonnes : mois (AAAA-MM), enveloppes facturées. Uniquement des dates et des nombres : aucune formule possible. */
    @GetMapping(value = "/api/v1/usage.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        StringBuilder csv = new StringBuilder("mois,enveloppes\r\n");
        releve.executer(du, au).parMois().forEach((mois, n) -> csv.append(mois).append(',').append(n).append("\r\n"));
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"consommation-" + du + "-" + au + ".csv\"").body(csv.toString());
    }
}
