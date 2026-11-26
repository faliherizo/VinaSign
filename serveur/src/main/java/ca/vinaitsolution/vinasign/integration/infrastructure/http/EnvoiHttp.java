package ca.vinaitsolution.vinasign.integration.infrastructure.http;

import ca.vinaitsolution.vinasign.integration.application.port.EnvoiWebhook;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** POST du webhook (porté de {@code RappelHttp} du CLM) : aucune redirection suivie, délais bornés, réponse ignorée. */
class EnvoiHttp implements EnvoiWebhook {

    private final HttpClient http;
    private final Duration delai;

    EnvoiHttp(Duration delai) {
        this.delai = delai;
        this.http = HttpClient.newBuilder().connectTimeout(delai).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public int envoyer(String url, Map<String, String> entetes, byte[] corps) {
        try {
            HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(url)).timeout(delai).POST(HttpRequest.BodyPublishers.ofByteArray(corps));
            entetes.forEach(requete::header);
            return http.send(requete.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException | IllegalArgumentException e) {
            throw new Injoignable("Destination du webhook injoignable.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Injoignable("Envoi du webhook interrompu.", e);
        }
    }
}
