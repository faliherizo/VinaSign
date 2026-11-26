package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;

/**
 * Contenu d'une signature lue dans un PDF ({@code /Contents}) : l'emplacement réservé est bourré de zéros après l'objet
 * de la signature. Bouncy Castle 1.85 et suivantes refusent ces octets en trop au lieu de les ignorer ; on ne garde que
 * l'objet. De longueur définie, il est découpé tel quel ; de longueur indéfinie (BER, comme l'écrit Bouncy Castle), il est
 * lu jusqu'à sa fin. La vérification porte sur les attributs signés, toujours réencodés en DER par la norme CMS.
 */
public final class ContenuSignature {

    private ContenuSignature() {
    }

    /** L'objet ASN.1 en tête du contenu ; le contenu entier si son en-tête est illisible (la lecture CMS le refusera). */
    public static byte[] sansBourrage(byte[] contenu) {
        if (contenu == null || contenu.length < 2) {
            return contenu;
        }
        int premier = contenu[1] & 0xFF;
        if (premier < 0x80) {
            return tronquer(contenu, 2 + premier);
        }
        int octets = premier & 0x7F;
        if (octets == 0) {
            return premierObjet(contenu);
        }
        if (octets > 4 || contenu.length < 2 + octets) {
            return contenu;
        }
        long longueur = 0;
        for (int i = 0; i < octets; i++) {
            longueur = (longueur << 8) | (contenu[2 + i] & 0xFF);
        }
        return tronquer(contenu, 2 + octets + longueur);
    }

    private static byte[] premierObjet(byte[] contenu) {
        try (ASN1InputStream lecteur = new ASN1InputStream(contenu)) {
            ASN1Primitive objet = lecteur.readObject();
            return objet == null ? contenu : objet.getEncoded();
        } catch (IOException e) {
            throw new UncheckedIOException("Signature illisible : " + e.getMessage(), e);
        }
    }

    private static byte[] tronquer(byte[] contenu, long fin) {
        return fin >= contenu.length ? contenu : Arrays.copyOf(contenu, (int) fin);
    }
}
