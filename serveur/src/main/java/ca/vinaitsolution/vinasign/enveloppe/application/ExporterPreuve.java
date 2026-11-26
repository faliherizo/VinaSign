package ca.vinaitsolution.vinasign.enveloppe.application;

import ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.EmballagePreuve;
import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalPreuve;
import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.domain.Ancrage;
import ca.vinaitsolution.vinasign.enveloppe.domain.ChaineScellee;
import ca.vinaitsolution.vinasign.enveloppe.domain.Enveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.IdentifiantEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.domain.VersionDocument;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/**
 * Dossier de preuve (VS-073) : versions du document, certificat de réalisation, journal chaîné, ancrage qui le couvre, et un
 * manifeste des empreintes signé par le cachet : vérifiable hors de VinaSign avec openssl seul. L'export est consigné.
 */
public class ExporterPreuve {

    /** Contenus du dossier. */
    public record Contenus(VersionsDocuments versions, StockageDocuments stockage, JournalPreuve journal, ProduireCertificat certificat,
                           ca.vinaitsolution.vinasign.enveloppe.application.port.ArchivesDocuments archives) {
    }

    private final ServicesEnveloppe services;
    private final Contenus contenus;
    private final ScellementDocuments scellement;
    private final EmballagePreuve emballage;

    public ExporterPreuve(ServicesEnveloppe services, Contenus contenus, ScellementDocuments scellement, EmballagePreuve emballage) {
        this.services = services;
        this.contenus = contenus;
        this.scellement = scellement;
        this.emballage = emballage;
    }

    public byte[] executer(IdentifiantEnveloppe id) {
        Enveloppe e = services.trouver(id);
        SequencedMap<String, byte[]> fichiers = new LinkedHashMap<>();
        List<VersionDocument> versions = contenus.versions().de(e.id());
        for (VersionDocument v : versions) {
            fichiers.put("documents/" + v.nature() + ".pdf", lireVerifie(v));
        }
        // Dernier état archivé (VS-132), s'il diffère du final : la même révision, augmentée des horodatages d'archive.
        contenus.archives().dernier(e.id()).filter(a -> versions.stream().noneMatch(v -> v.cleObjet().equals(a.cleObjet())))
                .ifPresent(a -> fichiers.put("documents/archive.pdf", a.verifier(contenus.stockage().lire(a.cleObjet()))));
        fichiers.put("certificat.pdf", contenus.certificat().produire(e));
        fichiers.put("journal.json", emballage.json(Map.of("enveloppe", e.id().valeur().toString(), "maillons", contenus.journal().maillons(e.id()))));
        contenus.journal().derniereConsignation(e.id()).flatMap(contenus.journal()::couvrant).ifPresent(a -> ancrage(fichiers, a));
        fichiers.put("LISEZMOI.txt", contenus.certificat().texte("lisez-moi").getBytes(StandardCharsets.UTF_8));
        Map<String, String> manifeste = new LinkedHashMap<>();
        fichiers.forEach((nom, contenu) -> manifeste.put(nom, ChaineScellee.sha256(contenu)));
        byte[] octets = emballage.json(manifeste);
        fichiers.put("manifeste.json", octets);
        fichiers.put("manifeste.p7s", scellement.signerEmpreinte(HexFormat.of().parseHex(ChaineScellee.sha256(octets))));
        services.consigner(e, "preuve-exportee", Map.of());
        return emballage.zip(fichiers);
    }

    private void ancrage(SequencedMap<String, byte[]> fichiers, Ancrage a) {
        fichiers.put("ancrage.json", emballage.json(Map.of("depuis", a.depuis().toString(), "jusqua", a.jusqua().toString(), "evenements", a.nombre(),
                "empreinteAgregee", a.empreinteAgregee(), "ancreLe", a.ancreLe().toString())));
        fichiers.put("ancrage.p7s", a.signature());
    }

    private byte[] lireVerifie(VersionDocument v) {
        return v.verifier(contenus.stockage().lire(v.cleObjet()));
    }
}
