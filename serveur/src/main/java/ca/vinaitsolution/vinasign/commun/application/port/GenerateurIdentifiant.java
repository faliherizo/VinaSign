package ca.vinaitsolution.vinasign.commun.application.port;

import java.util.UUID;

/** Source des identifiants : jamais de valeur en dur, et des tests déterministes par substitution. */
public interface GenerateurIdentifiant {

    UUID suivant();
}
