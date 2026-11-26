package ca.vinaitsolution.vinasign.acces.application.port;

import ca.vinaitsolution.vinasign.acces.domain.ClesPubliques;

/** Vérifie un jeu de clés publiques déclaré (JWKS) ; {@code ClesPubliquesInvalides} si une règle n'est pas respectée. */
public interface VerificateurClesPubliques {

    ClesPubliques verifier(String jwks);
}
