package ca.vinaitsolution.vinasign.organisation.application.port;

import java.util.List;

/** Lecture des enregistrements TXT publiés sous un nom DNS (preuve de domaine, VS-122) ; aucun enregistrement : liste vide. */
public interface ResolutionDns {

    List<String> textes(String nom);
}
