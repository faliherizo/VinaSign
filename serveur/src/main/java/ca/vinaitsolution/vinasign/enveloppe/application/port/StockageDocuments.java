package ca.vinaitsolution.vinasign.enveloppe.application.port;

/** Stockage objet privé des documents. Un objet écrit ne se réécrit pas (VS-022). */
public interface StockageDocuments {

    /** {@code VersionImmuable} si un objet existe déjà sous cette clé. */
    void ecrire(String cle, byte[] contenu);

    byte[] lire(String cle);
}
