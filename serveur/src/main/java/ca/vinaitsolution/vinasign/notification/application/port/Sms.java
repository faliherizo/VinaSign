package ca.vinaitsolution.vinasign.notification.application.port;

/** Fournisseur de SMS (VS-081), à choisir en phase 0 ; simulateur sur un poste et en bac à sable. */
public interface Sms {

    void envoyer(String numero, String texte);
}
