package ca.vinaitsolution.vinasign.enveloppe.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Zone de signature d'un signataire à qui l'expéditeur n'en a placé aucune (VS-070). Sans zone, la finalisation
 * n'apposerait rien : la signature, dessinée ou tapée, serait recueillie et scellée sans apparaître sur le document.
 * Les zones manquantes vont en bas de la dernière page, trois par rangée, de bas en haut, dans l'ordre des signataires.
 */
public final class ZonesSignatureParDefaut {

    static final int PAR_RANGEE = 3;
    static final double MARGE = 0.05;
    static final double PAS_X = 0.31;
    static final double LARGEUR = 0.28;
    static final double HAUTEUR = 0.07;
    static final double BAS = 0.88;
    static final double PAS_Y = 0.09;

    private ZonesSignatureParDefaut() {
    }

    /** Signataires sans zone de signature, dans leur ordre de signature. */
    public static List<Signataire> sansZone(List<Signataire> signataires, Set<UUID> avecZone) {
        return signataires.stream().filter(s -> !avecZone.contains(s.id()))
                .sorted((a, b) -> Integer.compare(a.ordre(), b.ordre())).toList();
    }

    /**
     * Zone du {@code rang}-ième signataire sans zone (à partir de 0). Au-delà de ce que la page contient, les rangées
     * restent en haut de page plutôt que d'en sortir.
     */
    public static Champ zone(UUID id, Signataire signataire, int rang, String type, String libelle, int page) {
        int colonne = rang % PAR_RANGEE;
        double y = Math.max(0, BAS - (rang / PAR_RANGEE) * PAS_Y);
        return new Champ(id, signataire.id(), type, libelle, page, MARGE + colonne * PAS_X, y, LARGEUR, HAUTEUR, true);
    }
}
