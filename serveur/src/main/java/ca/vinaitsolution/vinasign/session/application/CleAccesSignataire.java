package ca.vinaitsolution.vinasign.session.application;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.application.port.SignataireCourant;
import ca.vinaitsolution.vinasign.commun.domain.Condensats;
import ca.vinaitsolution.vinasign.commun.domain.IdentiteSignataire;
import ca.vinaitsolution.vinasign.session.application.port.ClesAcces;
import ca.vinaitsolution.vinasign.session.application.port.VerificationWebAuthn;
import ca.vinaitsolution.vinasign.session.domain.AucuneCleAcces;
import ca.vinaitsolution.vinasign.session.domain.AuthentificationPrealableRequise;
import ca.vinaitsolution.vinasign.session.domain.CleAcces;
import ca.vinaitsolution.vinasign.session.domain.CleAccesRefusee;
import ca.vinaitsolution.vinasign.session.domain.DefiCleAcces;
import ca.vinaitsolution.vinasign.session.domain.LienInvalide;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Clés d'accès des signataires récurrents (VS-043). L'enregistrement suit une première authentification (code ou attestation) ;
 * l'usage élève la session au niveau configuré, méthode {@code hwk}, consignée dans la preuve. Sans clé, le signataire se
 * rabat sur le code. La clé appartient à l'identité du signataire dans l'organisation (sujet externe, sinon courriel).
 */
public class CleAccesSignataire {

    static final String AMR = "hwk";

    /** Partie de confiance, origine de l'interface du signataire, niveau accordé, durée d'un défi. */
    public record Reglages(String rpId, String origine, String niveau, Duration dureeDefi) {
    }

    /** Ce dont la cérémonie a besoin, côté navigateur (navigator.credentials) ; identifiants en base64url. */
    public record Options(String defi, String rpId, String identifiantUtilisateur, String nomAffiche, List<String> clesPermises, long delaiMs) {
    }

    /** Sources : signataire courant, enveloppes, clés, vérification. */
    public record Sources(SignataireCourant signataire, LectureEnveloppes enveloppes, ClesAcces cles, VerificationWebAuthn verification) {
    }

    private final Sources sources;
    private final ServicesSession services;
    private final Reglages reglages;

    public CleAccesSignataire(Sources sources, ServicesSession services, Reglages reglages) {
        this.sources = sources;
        this.services = services;
        this.reglages = reglages;
    }

    public Options optionsEnregistrement() {
        IdentiteSignataire s = signataire();
        if (sources.cles().niveauSession(s.session()).isEmpty()) {
            throw new AuthentificationPrealableRequise();
        }
        DossierEnveloppe.SignataireDossier d = dossier(s);
        return options(s, DefiCleAcces.ENREGISTREMENT, d.nom(), List.of(), identite(s, d));
    }

    public void enregistrer(byte[] attestationObject, byte[] clientDataJson) {
        IdentiteSignataire s = signataire();
        VerificationWebAuthn.Enregistree e = sources.verification().enregistrer(ceremonie(s, DefiCleAcces.ENREGISTREMENT), attestationObject, clientDataJson);
        sources.cles().ajouter(new CleAcces(services.identifiants().suivant(), identite(s, dossier(s)), e.credentialId(), e.donnees(), e.compteur(),
                services.horloge().instant()));
    }

    public Options optionsAuthentification() {
        IdentiteSignataire s = signataire();
        DossierEnveloppe.SignataireDossier d = dossier(s);
        String identite = identite(s, d);
        List<String> cles = sources.cles().de(identite).stream().map(CleAcces::credentialId).toList();
        if (cles.isEmpty()) {
            throw new AucuneCleAcces();
        }
        return options(s, DefiCleAcces.AUTHENTIFICATION, d.nom(), cles, identite);
    }

    public void authentifier(VerificationWebAuthn.Assertion assertion) {
        IdentiteSignataire s = signataire();
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(assertion.credentialId());
        CleAcces cle = sources.cles().de(identite(s, dossier(s))).stream().filter(c -> c.credentialId().equals(id)).findFirst()
                .orElseThrow(() -> new CleAccesRefusee("Clé d'accès inconnue pour ce signataire."));
        long compteur = sources.verification().authentifier(ceremonie(s, DefiCleAcces.AUTHENTIFICATION), cle.donnees(), cle.compteur(), assertion);
        Instant maintenant = services.horloge().instant();
        sources.cles().utiliser(cle.id(), compteur, maintenant);
        sources.cles().authentifier(s.session(), reglages.niveau(), AMR);
    }

    private Options options(IdentiteSignataire s, String nature, String nom, List<String> cles, String identite) {
        byte[] defi = Base64.getUrlDecoder().decode(services.nouveauJeton());
        sources.cles().defier(new DefiCleAcces(s.session(), nature, defi, services.horloge().instant().plus(reglages.dureeDefi())));
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return new Options(b64.encodeToString(defi), reglages.rpId(), b64.encodeToString(HexFormat.of().parseHex(identite)), nom, cles,
                reglages.dureeDefi().toMillis());
    }

    private VerificationWebAuthn.Ceremonie ceremonie(IdentiteSignataire s, String nature) {
        byte[] defi = sources.cles().consommerDefi(s.session()).orElseThrow(() -> new CleAccesRefusee("Aucun défi en cours : recommencer."))
                .exiger(nature, services.horloge().instant());
        return new VerificationWebAuthn.Ceremonie(defi, reglages.origine(), reglages.rpId());
    }

    private IdentiteSignataire signataire() {
        return sources.signataire().courant().orElseThrow(LienInvalide::new);
    }

    private DossierEnveloppe.SignataireDossier dossier(IdentiteSignataire s) {
        return sources.enveloppes().dossier(s.enveloppe()).flatMap(d -> d.signataires().stream().filter(x -> x.id().equals(s.signataire())).findFirst())
                .orElseThrow(LienInvalide::new);
    }

    /** Empreinte de l'organisation et du sujet externe (sinon du courriel) : la même personne retrouve sa clé d'une enveloppe à l'autre. */
    static String identite(IdentiteSignataire s, DossierEnveloppe.SignataireDossier d) {
        String personne = d.sujetExterne() != null ? "sujet:" + d.sujetExterne() : "courriel:" + d.courriel().toLowerCase(Locale.ROOT);
        return Condensats.sha256Hex(s.organisation().valeur() + ":" + personne);
    }
}
