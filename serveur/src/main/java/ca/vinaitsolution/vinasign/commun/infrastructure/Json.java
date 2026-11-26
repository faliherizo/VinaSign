package ca.vinaitsolution.vinasign.commun.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lecture et écriture JSON des adaptateurs (journal, idempotence, dossier de preuve) : une erreur Jackson devient une erreur
 * non contrôlée qui dit quoi n'a pas pu être lu ou écrit. Une seule traduction pour la plateforme.
 */
public final class Json {

    private Json() {
    }

    public static String ecrire(ObjectMapper json, Object valeur, String quoi) {
        try {
            return json.writeValueAsString(valeur);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(quoi + " non sérialisable.", e);
        }
    }

    public static <T> T lire(ObjectMapper json, String texte, TypeReference<T> type, String quoi) {
        try {
            return json.readValue(texte, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(quoi + " illisible.", e);
        }
    }
}
