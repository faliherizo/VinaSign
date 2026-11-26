package ca.vinaitsolution.vinasign.integration.application.port;

/** Garde anti-SSRF des URL déclarées par une application (OWASP A10) ; lève {@code DestinationInvalide}. */
public interface GardeDestinations {

    void exiger(String url);
}
