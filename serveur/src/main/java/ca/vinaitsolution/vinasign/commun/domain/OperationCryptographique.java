package ca.vinaitsolution.vinasign.commun.domain;

import java.security.GeneralSecurityException;

/**
 * Opération cryptographique du JDK (HMAC, chiffrement, signature) dont l'échec, impossible avec un JDK conforme et une clé
 * déjà validée, devient une erreur non contrôlée nommant l'opération : une seule traduction pour toute la plateforme.
 */
@FunctionalInterface
public interface OperationCryptographique<T> {

    T executer() throws GeneralSecurityException;

    static <T> T executer(String operation, OperationCryptographique<T> o) {
        try {
            return o.executer();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(operation + " impossible.", e);
        }
    }
}
