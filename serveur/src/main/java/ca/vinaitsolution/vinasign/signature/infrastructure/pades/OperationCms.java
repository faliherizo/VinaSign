package ca.vinaitsolution.vinasign.signature.infrastructure.pades;

/**
 * Opération BouncyCastle ou PDFBox aux exceptions contrôlées multiples (CMS, opérateur, E/S, sécurité) : toute erreur
 * devient une {@link IllegalStateException} qui nomme l'opération. Une seule traduction pour le cachet.
 */
@FunctionalInterface
interface OperationCms<T> {

    T executer() throws Exception;

    static <T> T executer(String operation, OperationCms<T> o) {
        try {
            return o.executer();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(operation + " impossible : " + e.getMessage(), e);
        }
    }
}
