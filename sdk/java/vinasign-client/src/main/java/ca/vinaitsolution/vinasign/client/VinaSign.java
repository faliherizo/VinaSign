package ca.vinaitsolution.vinasign.client;

import ca.vinaitsolution.vinasign.client.genere.ApiClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * Point d'entrée du SDK : un {@link ApiClient} généré depuis le contrat OpenAPI v1, dont chaque requête porte le jeton
 * d'accès de l'application. Les API générées se construisent dessus, par exemple
 * {@code new EnveloppeControllerApi(vinaSign.apiClient())}.
 */
public final class VinaSign {

    private final ApiClient api;
    private final JetonsVinaSign jetons;

    public VinaSign(JetonsVinaSign.Identite identite, Clock horloge) {
        HttpClient.Builder http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER);
        this.jetons = new JetonsVinaSign(identite, http.build(), horloge);
        URI serveur = identite.serveur();
        this.api = new ApiClient();
        api.setHttpClientBuilder(http);
        api.updateBaseUri(serveur.toString().replaceAll("/+$", ""));
        api.setRequestInterceptor(requete -> requete.header("Authorization", "Bearer " + jetons.jeton()));
    }

    public ApiClient apiClient() {
        return api;
    }

    public JetonsVinaSign jetons() {
        return jetons;
    }
}
