package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.application.dto.Certificat;

/** Mise en page du certificat (VS-072) : un document HTML autonome, converti ensuite en PDF/A. */
public interface RenduCertificat {

    byte[] html(Certificat certificat);
}
