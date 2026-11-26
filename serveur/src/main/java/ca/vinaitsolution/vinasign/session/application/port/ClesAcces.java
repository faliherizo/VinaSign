package ca.vinaitsolution.vinasign.session.application.port;

import ca.vinaitsolution.vinasign.session.domain.CleAcces;
import ca.vinaitsolution.vinasign.session.domain.DefiCleAcces;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Clés d'accès, défis et niveau de la session (VS-043), sous la RLS de l'organisation. */
public interface ClesAcces {

    Optional<String> niveauSession(UUID session);

    /** Remplace le défi en cours de la session. */
    void defier(DefiCleAcces defi);

    /** Le défi de la session, retiré : il ne sert qu'une fois. */
    Optional<DefiCleAcces> consommerDefi(UUID session);

    List<CleAcces> de(String identite);

    void ajouter(CleAcces cle);

    void utiliser(UUID cle, long compteur, Instant le);

    /** La session atteint ce niveau par cette méthode (amr). */
    void authentifier(UUID session, String niveau, String amr);
}
