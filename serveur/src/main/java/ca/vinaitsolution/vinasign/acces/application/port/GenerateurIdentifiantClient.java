package ca.vinaitsolution.vinasign.acces.application.port;

import ca.vinaitsolution.vinasign.acces.domain.IdentifiantClient;

/** Identifiants de client non prévisibles. */
public interface GenerateurIdentifiantClient {

    IdentifiantClient suivant();
}
