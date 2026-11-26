package ca.vinaitsolution.vinasign.enveloppe.application.dto;

import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.EvenementEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Enveloppe vue par son application : statuts en codes de référentiel ; aucun courriel renvoyé. */
public record VueEnveloppe(UUID id, String referenceExterne, String mode, String statut, Instant expireLe, Instant creeLe,
                           List<VueSignataire> signataires, List<VueVersion> documents, List<VueEvenement> evenements) {

    /** @param peutAgir vrai si c'est son tour */
    public record VueSignataire(UUID id, String nom, String langue, String type, int ordre, String statut, boolean peutAgir, String niveauExige) {
    }

    public record VueVersion(UUID id, String nature, String sha256, long taille, int pages, Instant creeLe) {
    }

    public record VueEvenement(String type, Instant survenuLe) {
    }

    public static VueEnveloppe de(Enveloppe e, List<VersionDocument> versions) {
        Set<UUID> tour = e.peuventAgir().stream().map(Signataire::id).collect(Collectors.toSet());
        return new VueEnveloppe(e.id().valeur(), e.referenceExterne(), e.mode().code(), e.statut().code(), e.expireLe(), e.creeLe(),
                e.signataires().stream().sorted(Comparator.comparingInt(Signataire::ordre).thenComparing(Signataire::nom))
                        .map(s -> new VueSignataire(s.id(), s.nom(), s.langue(), s.type(), s.ordre(), s.statut().code(), tour.contains(s.id()), s.niveauExige()))
                        .toList(),
                versions.stream().map(v -> new VueVersion(v.id(), v.nature(), v.sha256(), v.taille(), v.pages(), v.creeLe())).toList(),
                null);
    }

    public VueEnveloppe avecEvenements(List<EvenementEnveloppe> journal) {
        return new VueEnveloppe(id, referenceExterne, mode, statut, expireLe, creeLe, signataires, documents,
                journal.stream().map(ev -> new VueEvenement(ev.type(), ev.survenuLe())).toList());
    }
}
