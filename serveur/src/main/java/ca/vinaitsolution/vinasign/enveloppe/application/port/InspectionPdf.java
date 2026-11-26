package ca.vinaitsolution.vinasign.enveloppe.application.port;

/** Lecture de la structure d'un PDF : rend son nombre de pages ; {@code DocumentRefuse} s'il n'est pas un PDF lisible. */
public interface InspectionPdf {

    int pages(byte[] contenu);
}
