package ca.vinaitsolution.vinasign.signature.infrastructure.verification;

import static org.junit.jupiter.api.Assertions.*;

import ca.vinaitsolution.vinasign.signature.application.dto.RapportVerification;
import ca.vinaitsolution.vinasign.signature.cle.Certificats;
import ca.vinaitsolution.vinasign.signature.infrastructure.cle.ClePkcs12;
import ca.vinaitsolution.vinasign.signature.infrastructure.pades.CachetPades;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class VerificateurPadesTest {

    @TempDir
    static Path dossier;
    private static CachetPades cachet;
    private final VerificateurPades verificateur = new VerificateurPades();

    @BeforeAll
    static void cachet() {
        cachet = new CachetPades(new ClePkcs12(Certificats.magasin(dossier, "RSA", "s".toCharArray()), "s".toCharArray()), null, Clock.systemUTC());
    }

    public static byte[] pdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(o);
            return o.toByteArray();
        }
    }

    @Test
    void un_pdf_scelle_est_integre_et_son_signataire_nomme() throws Exception {
        RapportVerification r = verificateur.pdf(cachet.scellerPdf(pdf(), "Vérification"));
        assertTrue(r.integre());
        assertEquals("PAdES-B-B", r.niveau());
        assertEquals(1, r.signatures().size());
        assertTrue(r.signatures().get(0).signataire().contains("Cachet d'essai"));
        assertNull(r.signatures().get(0).horodatage());
    }

    @Test
    void une_alteration_ou_une_revision_visible_apres_la_signature_rompt_l_integrite() throws Exception {
        byte[] scelle = cachet.scellerPdf(pdf(), "Vérification");
        assertFalse(verificateur.pdf(altererPage(scelle)).integre(), "un octet signé modifié (taille de la page)");
        assertFalse(verificateur.pdf(revision(scelle, doc -> {
            PDPage page = new PDPage();
            page.getCOSObject().setNeedToBeUpdated(true);
            doc.getPages().add(page);
        })).integre(), "une page ajoutée après la signature");
        assertFalse(verificateur.pdf(revision(scelle, doc -> {
            PDPage page = doc.getPage(0);
            try (var flux = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page,
                    org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, false)) {
                flux.addRect(10, 10, 50, 50);
                flux.fill();
            }
            page.getCOSObject().setNeedToBeUpdated(true);
        })).integre(), "un contenu de page modifié après la signature");
        assertFalse(verificateur.pdf(revision(scelle, doc -> {
            PDPage page = doc.getPage(0);
            var note = new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText();
            note.setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(10, 10, 20, 20));
            note.getCOSObject().setNeedToBeUpdated(true);
            page.getAnnotations().add(note);
            page.getCOSObject().setNeedToBeUpdated(true);
        })).integre(), "une annotation ajoutée après la signature");
    }

    @Test
    void des_octets_ou_une_revision_sans_effet_visible_apres_la_signature_n_alterent_rien() throws Exception {
        byte[] scelle = cachet.scellerPdf(pdf(), "Vérification");
        assertTrue(verificateur.pdf(java.util.Arrays.copyOf(scelle, scelle.length + 10)).integre(), "octets nuls après %%EOF : ignorés des lecteurs");
        assertTrue(verificateur.pdf(revision(scelle, doc -> doc.getDocumentInformation().setKeywords("dss"))).integre(),
                "une révision de métadonnées, comme l'ajout des données de validation");
    }

    @Test
    void une_signature_illisible_ou_sans_certificat_n_est_jamais_valide() throws Exception {
        byte[] scelle = cachet.scellerPdf(pdf(), "Vérification");
        String texte = new String(scelle, StandardCharsets.ISO_8859_1);
        int contenu = texte.indexOf("/Contents <") + "/Contents <".length();
        byte[] corrompu = scelle.clone();
        for (int i = 0; i < 16; i++) {
            corrompu[contenu + i] = 'F';
        }
        RapportVerification r = verificateur.pdf(corrompu);
        assertFalse(r.integre());
        assertFalse(r.signatures().get(0).valide(), "CMS illisible : signature invalide, sans exception");

        byte[] sha = MessageDigest.getInstance("SHA-256").digest("note".getBytes(StandardCharsets.UTF_8));
        org.bouncycastle.cms.CMSSignedData avec = new org.bouncycastle.cms.CMSSignedData(cachet.signerEmpreinte(sha));
        byte[] sansCertificat = org.bouncycastle.cms.CMSSignedData.replaceCertificatesAndCRLs(avec, new org.bouncycastle.util.CollectionStore<>(java.util.List.of()),
                new org.bouncycastle.util.CollectionStore<>(java.util.List.of()), new org.bouncycastle.util.CollectionStore<>(java.util.List.of())).getEncoded();
        assertFalse(verificateur.empreinte(sha, sansCertificat).integre(), "sans certificat du signataire : rien à vérifier");
    }

    @Test
    void sans_plage_signee_aucune_revision_n_est_admise_et_une_verification_qui_echoue_rend_son_repli() throws Exception {
        byte[] p = pdf();
        try (PDDocument doc = PDDocument.load(p)) {
            assertFalse(VerificateurPades.revisionsUlterieuresSansEffetVisible(doc, p, 0));
        }
        assertEquals("repli", VerificateurPades.essayer(() -> {
            throw new java.security.SignatureException("signature fausse");
        }, "repli"));
        assertEquals("ok", VerificateurPades.essayer(() -> "ok", "repli"));
    }

    /** Change un chiffre de la /MediaBox, dans la plage d'octets couverte par la signature. */
    private static byte[] altererPage(byte[] signe) {
        String texte = new String(signe, StandardCharsets.ISO_8859_1);
        int i = texte.indexOf("/MediaBox");
        assertTrue(i > 0);
        int chiffre = i;
        while (!Character.isDigit(texte.charAt(chiffre)) || texte.charAt(chiffre) == '0') {
            chiffre++;
        }
        byte[] altere = signe.clone();
        altere[chiffre] = (byte) (altere[chiffre] == '9' ? '8' : altere[chiffre] + 1);
        return altere;
    }

    /** Ajoute une révision incrémentale (sans signature) après la dernière signature. */
    private static byte[] revision(byte[] signe, Modification modification) throws Exception {
        try (PDDocument doc = PDDocument.load(signe); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            modification.appliquer(doc);
            doc.getDocumentCatalog().getCOSObject().setNeedToBeUpdated(true);
            doc.getPages().getCOSObject().setNeedToBeUpdated(true);
            doc.getDocumentInformation().getCOSObject().setNeedToBeUpdated(true);
            doc.saveIncremental(o);
            return o.toByteArray();
        }
    }

    private interface Modification {
        void appliquer(PDDocument doc) throws Exception;
    }

    @Test
    void un_pdf_sans_signature_n_est_pas_integre_et_un_fichier_illisible_est_refuse() throws Exception {
        RapportVerification r = verificateur.pdf(pdf());
        assertFalse(r.integre());
        assertEquals("", r.niveau());
        assertThrows(IllegalArgumentException.class, () -> verificateur.pdf("%PDF-1.7 tronqué".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void une_empreinte_est_verifiee_contre_sa_signature_et_rien_d_autre() throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-256").digest("note".getBytes(StandardCharsets.UTF_8));
        byte[] cms = cachet.signerEmpreinte(sha);
        RapportVerification r = verificateur.empreinte(sha, cms);
        assertTrue(r.integre());
        assertEquals("CAdES-B-B", r.niveau());
        assertFalse(verificateur.empreinte(MessageDigest.getInstance("SHA-256").digest("autre".getBytes()), cms).integre());
        RapportVerification illisible = verificateur.empreinte(sha, new byte[] {1, 2, 3});
        assertFalse(illisible.integre());
        assertTrue(illisible.signatures().isEmpty());
    }
}
