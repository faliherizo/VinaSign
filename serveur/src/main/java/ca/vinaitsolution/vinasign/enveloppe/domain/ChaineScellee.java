package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Journal scellé d'une enveloppe (VS-071, porté de {@code ChaineScellee} du CLM) : chaque événement porte l'empreinte du
 * précédent, de sorte qu'une modification, une suppression ou une insertion a posteriori rompt la chaîne et se détecte.
 * La forme canonique est celle de la fonction {@code journal_empreinte} de la base, qui calcule la chaîne à l'insertion.
 */
public final class ChaineScellee {

    public static final String ORIGINE = "0".repeat(64);

    /** @param microsecondes heure de l'événement, en microsecondes depuis l'epoch ; {@code donnees} : JSON tel que la base le rend */
    public record Maillon(long sequence, String type, String acteur, long microsecondes, String donnees, String empreintePrecedente,
                          String empreinte) {
    }

    /** Résultat d'une vérification : {@code rupture} désigne le premier rang fautif. */
    public record Verification(boolean integre, Optional<Long> rupture) {
    }

    private ChaineScellee() {
    }

    public static String empreinte(String precedente, UUID enveloppe, Maillon m) {
        String canon = String.join("|", precedente, enveloppe.toString(), Long.toString(m.sequence()), nul(m.type()), nul(m.acteur()),
                Long.toString(m.microsecondes()), m.donnees() == null ? "" : m.donnees());
        return sha256(canon.getBytes(StandardCharsets.UTF_8));
    }

    public static Verification verifier(UUID enveloppe, List<Maillon> maillons) {
        String precedente = ORIGINE;
        long attendue = 1;
        for (Maillon m : maillons) {
            if (m.sequence() != attendue || !precedente.equals(m.empreintePrecedente()) || !empreinte(precedente, enveloppe, m).equals(m.empreinte())) {
                return new Verification(false, Optional.of(attendue));
            }
            precedente = m.empreinte();
            attendue++;
        }
        return new Verification(true, Optional.empty());
    }

    /** Empreinte agrégée d'une suite d'empreintes, dans l'ordre de consignation (ancrage horaire). */
    public static String agreger(List<String> empreintes) {
        return sha256(String.join("", empreintes).getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] octets) {
        return condenser("SHA-256", octets);
    }

    /** L'indisponibilité d'un algorithme devient une erreur typée plutôt qu'une exception contrôlée propagée. */
    static String condenser(String algorithme, byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithme).digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithme + " indisponible", e);
        }
    }

    private static String nul(String s) {
        return s == null ? "" : s.replace("|", "¦");
    }
}
