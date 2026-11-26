package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Mesure de la consommation par enveloppe envoyée (VS-120, portée de {@code Enveloppes} du CLM) : une enveloppe se compte
 * une fois, au mois de son premier envoi, quel que soit le nombre de documents ou de signataires. Une enveloppe annulée
 * avant tout envoi ne compte pas ; annulée après, elle compte, puisque l'invitation est partie.
 */
public final class Consommation {

    public static final String ENVOI = "enveloppe-envoyee";
    static final String COMPLETEE = "enveloppe-completee";
    static final String REFUSEE = "signataire-a-refuse";
    static final String ANNULEE = "enveloppe-annulee";
    static final String EXPIREE = "enveloppe-expiree";

    public record Evenement(UUID enveloppe, String type, Instant le) {
    }

    /**
     * @param envoyees           enveloppes dont le premier envoi tombe dans la période (facturées)
     * @param annuleesApresEnvoi facturées, puisque l'invitation est partie
     * @param annuleesAvantEnvoi jamais facturées
     * @param parMois            enveloppes facturées, par mois du premier envoi
     */
    public record Releve(int envoyees, int completees, int refusees, int annuleesApresEnvoi, int annuleesAvantEnvoi, int expirees, int renvois,
                         Map<YearMonth, Integer> parMois) {
    }

    private Consommation() {
    }

    /** @param dejaEnvoyees enveloppes envoyées avant la période : un nouvel envoi n'est qu'un renvoi */
    public static Releve compter(List<Evenement> periode, Set<UUID> dejaEnvoyees, ZoneId fuseau) {
        Set<UUID> envoyees = new HashSet<>(dejaEnvoyees);
        Map<YearMonth, Integer> parMois = new TreeMap<>();
        int[] n = new int[7];
        for (Evenement e : periode.stream().sorted(Comparator.comparing(Evenement::le)).toList()) {
            boolean envoyee = envoyees.contains(e.enveloppe());
            switch (e.type()) {
                case ENVOI -> {
                    if (envoyees.add(e.enveloppe())) {
                        n[0]++;
                        parMois.merge(YearMonth.from(e.le().atZone(fuseau)), 1, Integer::sum);
                    } else {
                        n[6]++;
                    }
                }
                case COMPLETEE -> n[1] += envoyee ? 1 : 0;
                case REFUSEE -> n[2] += envoyee ? 1 : 0;
                case ANNULEE -> n[envoyee ? 3 : 4]++;
                case EXPIREE -> n[5] += envoyee ? 1 : 0;
                default -> {
                    // Les autres événements du journal ne changent pas la mesure.
                }
            }
        }
        return new Releve(n[0], n[1], n[2], n[3], n[4], n[5], n[6], parMois);
    }
}
