package ca.vinaitsolution.vinasign.commun.application.port;

/** Chiffrement authentifié des champs sensibles (téléphone, VS-081) : seul le chiffré est conservé. */
public interface Chiffrement {

    byte[] chiffrer(String clair);

    String dechiffrer(byte[] chiffre);
}
