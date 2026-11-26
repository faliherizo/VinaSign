package ca.vinaitsolution.vinasign.enveloppe.application.port;

import java.util.Optional;

/** Analyse antivirale d'un contenu déposé. */
public interface AnalyseAntivirus {

    /** Nom de la menace détectée, ou vide si le contenu est sain ; une analyse impossible lève une exception. */
    Optional<String> analyser(byte[] contenu);
}
