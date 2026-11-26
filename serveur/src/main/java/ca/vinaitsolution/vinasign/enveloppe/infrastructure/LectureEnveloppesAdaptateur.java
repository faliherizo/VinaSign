package ca.vinaitsolution.vinasign.enveloppe.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.Enveloppes;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.Signataire;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Le contexte « enveloppe » se laisse lire par les autres par ce DTO, sous la RLS de l'organisation courante. */
@Component
class LectureEnveloppesAdaptateur implements LectureEnveloppes {

    private final Enveloppes enveloppes;

    LectureEnveloppesAdaptateur(Enveloppes enveloppes) {
        this.enveloppes = enveloppes;
    }

    @Override
    public Optional<DossierEnveloppe> dossier(UUID id) {
        return enveloppes.parId(new IdentifiantEnveloppe(id)).map(LectureEnveloppesAdaptateur::dossier);
    }

    static DossierEnveloppe dossier(Enveloppe e) {
        Set<UUID> tour = e.peuventAgir().stream().map(Signataire::id).collect(Collectors.toSet());
        return new DossierEnveloppe(e.id().valeur(), e.organisation().valeur(), e.application(), e.referenceExterne(), e.mode().code(),
                e.statut().code(), e.expireLe(), e.signataires().stream().sorted(Comparator.comparingInt(Signataire::ordre))
                        .map(s -> new DossierEnveloppe.SignataireDossier(s.id(), s.nom(), s.courriel(), s.langue(), s.type(), s.ordre(),
                                s.statut().code(), s.sujetExterne(), tour.contains(s.id()), s.telephone(), s.niveauExige()))
                        .toList());
    }
}
