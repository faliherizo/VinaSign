package ca.vinaitsolution.vinasign.enveloppe.application.port;

import ca.vinaitsolution.vinasign.enveloppe.domain.Apposition;
import java.util.List;

/** Appose signatures et valeurs sur le PDF, sans en changer les pages (VS-070). */
public interface CompositionDocument {

    byte[] composer(byte[] pdf, List<Apposition> appositions);
}
