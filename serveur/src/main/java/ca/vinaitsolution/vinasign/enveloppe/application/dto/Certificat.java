package ca.vinaitsolution.vinasign.enveloppe.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contenu du certificat de réalisation (VS-072), libellés déjà résolus dans la langue du certificat : le rendu ne
 * décide de rien, il présente.
 *
 * @param textes textes fixes du certificat (référentiel texte-certificat), par code
 */
public record Certificat(String langue, Map<String, String> textes, String identifiant, String reference, String statut, Instant creeLe,
                         List<Document> documents, List<Signataire> signataires, List<Evenement> chronologie, Integrite integrite,
                         Instant genereLe) {

    public Certificat {
        textes = Map.copyOf(textes);
        documents = List.copyOf(documents);
        signataires = List.copyOf(signataires);
        chronologie = List.copyOf(chronologie);
    }

    public record Document(String nature, String sha256) {
    }

    /** @param consentement version et empreinte du texte accepté, ou null */
    public record Signataire(int ordre, String nom, String statut, Instant agiLe, String consentement, Instant consentiLe, String niveau,
                             List<String> methodes) {
    }

    public record Evenement(Instant le, String libelle, String acteur) {
    }

    /** @param ancrage empreinte agrégée et heure de l'ancrage qui couvre le journal, ou null */
    public record Integrite(boolean integre, int evenements, String ancrage, Instant ancreLe) {
    }
}
