package ca.vinaitsolution.vinasign.enveloppe.application.port;

import java.util.SequencedMap;

/** Mise en forme du dossier de preuve (VS-073) : JSON lisible et archive ZIP, dans l'ordre des fichiers donnés. */
public interface EmballagePreuve {

    byte[] json(Object valeur);

    byte[] zip(SequencedMap<String, byte[]> fichiers);
}
