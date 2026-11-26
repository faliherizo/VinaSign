package ca.vinaitsolution.vinasign.session.application.port;

import ca.vinaitsolution.vinasign.session.domain.LienResolu;
import ca.vinaitsolution.vinasign.session.domain.LienSignature;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Liens des signataires de l'organisation courante. */
public interface LiensSignature {

    void ajouter(LienSignature lien);

    void remplacer(LienSignature lien);

    /** Lien de ce jeton, quelle que soit l'organisation : seule lecture permise avant l'authentification du signataire. */
    Optional<LienResolu> parEmpreinte(String empreinteJeton);

    /** Liens non consommés, non révoqués et non expirés de ce signataire. */
    List<LienSignature> actifs(UUID signataire, Instant maintenant);

    /** Révoque ce lien s'il est encore utilisable. */
    void revoquer(UUID lien, Instant le);

    /** Révoque tous les liens encore utilisables de l'enveloppe ; rend leur nombre. */
    int revoquerDe(UUID enveloppe, Instant le);
}
