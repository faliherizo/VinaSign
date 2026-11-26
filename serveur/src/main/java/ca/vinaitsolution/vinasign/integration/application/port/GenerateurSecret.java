package ca.vinaitsolution.vinasign.integration.application.port;

/** Secret de signature aléatoire (256 bits), injecté : les tests restent déterministes. */
public interface GenerateurSecret {

    byte[] nouveau();
}
