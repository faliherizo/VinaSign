package ca.vinaitsolution.vinasign.enveloppe.infrastructure.web;

import ca.vinaitsolution.vinasign.enveloppe.application.TelechargerDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Versions du document d'une enveloppe, dont le document final signé (VS-070), pour l'application qui l'a créée. */
@RestController
@Validated
public class DocumentController {

    private final TelechargerDocument telechargement;

    public DocumentController(TelechargerDocument telechargement) {
        this.telechargement = telechargement;
    }

    /** {@code nature} : code du référentiel nature-document. PDF jamais mis en cache. */
    @GetMapping(value = "/api/v1/envelopes/{id}/documents/{nature}", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SCOPE_documents-telecharger') or hasAnyRole('preparateur', 'auditeur')")
    public ResponseEntity<byte[]> telecharger(@PathVariable UUID id, @PathVariable @Pattern(regexp = "[a-z0-9-]{1,30}") String nature) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(telechargement.executer(new IdentifiantEnveloppe(id), nature));
    }
}
