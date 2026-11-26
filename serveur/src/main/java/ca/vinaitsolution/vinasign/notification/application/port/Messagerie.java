package ca.vinaitsolution.vinasign.notification.application.port;

/** Relais de courriel (SMTP). Une erreur d'envoi lève une exception : le message reste dans la boîte d'envoi. */
public interface Messagerie {

    void envoyer(String destinataire, String sujet, String corps);
}
