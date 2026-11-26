package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionIntrouvable;

/**
 * Une version du document de l'enveloppe (original, préparé, final signé), pour l'application qui l'a créée ; les
 * octets sont relus et leur empreinte revérifiée avant d'être rendus.
 */
public class TelechargerDocument {

    private final ServicesEnveloppe services;
    private final VersionsDocuments versions;
    private final StockageDocuments stockage;

    public TelechargerDocument(ServicesEnveloppe services, VersionsDocuments versions, StockageDocuments stockage) {
        this.services = services;
        this.versions = versions;
        this.stockage = stockage;
    }

    public byte[] executer(IdentifiantEnveloppe id, String nature) {
        Enveloppe e = services.trouver(id);
        VersionDocument v = versions.de(e.id()).stream().filter(x -> x.nature().equals(nature)).findFirst().orElseThrow(VersionIntrouvable::new);
        return v.verifier(stockage.lire(v.cleObjet()));
    }
}
