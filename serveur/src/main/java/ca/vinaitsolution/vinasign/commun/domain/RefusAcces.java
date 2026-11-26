package ca.vinaitsolution.vinasign.commun.domain;

/** Motif d'un refus d'accès : type stable du problème RFC 9457 et explication. */
public record RefusAcces(String type, String message) {

    public RefusAcces {
        if (type == null || type.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Un refus a un type et un message.");
        }
    }
}
