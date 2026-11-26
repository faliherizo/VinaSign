package ca.vinaitsolution.vinasign.notification.infrastructure.smtp;

import ca.vinaitsolution.vinasign.notification.application.port.Messagerie;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Relais SMTP (Mailpit sur un poste et en test, relais de l'organisation en exploitation) ; texte seul, sans pièce jointe. */
public class MessagerieSmtp implements Messagerie {

    private final JavaMailSender relais;
    private final String expediteur;

    public MessagerieSmtp(JavaMailSender relais, String expediteur) {
        this.relais = relais;
        this.expediteur = expediteur;
    }

    @Override
    public void envoyer(String destinataire, String sujet, String corps) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(expediteur);
        m.setTo(destinataire);
        m.setSubject(sujet);
        m.setText(corps);
        relais.send(m);
    }
}
