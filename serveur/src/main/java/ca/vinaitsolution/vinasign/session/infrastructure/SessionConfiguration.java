package ca.vinaitsolution.vinasign.session.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.application.port.PublicationEvenements;
import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.commun.infrastructure.VinaSignProprietes;
import ca.vinaitsolution.vinasign.referentiel.application.port.Referentiels;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import ca.vinaitsolution.vinasign.session.application.AuthentifierParCode;
import ca.vinaitsolution.vinasign.session.application.Consentir;
import ca.vinaitsolution.vinasign.session.application.port.DefisCode;
import ca.vinaitsolution.vinasign.session.application.port.AttestationsLiens;
import ca.vinaitsolution.vinasign.session.domain.ExigenceAttestation;
import ca.vinaitsolution.vinasign.commun.application.port.VerificateurAttestations;
import ca.vinaitsolution.vinasign.session.infrastructure.securite.EmpreinteHmac;
import ca.vinaitsolution.vinasign.session.application.EmettreLiens;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.session.application.AncetresIntegration;
import ca.vinaitsolution.vinasign.commun.application.port.OriginesIntegration;
import ca.vinaitsolution.vinasign.session.application.OuvrirSession;
import ca.vinaitsolution.vinasign.session.application.OuvrirSessionIntegree;
import ca.vinaitsolution.vinasign.session.application.RelancerSignataires;
import ca.vinaitsolution.vinasign.session.application.RevoquerLiens;
import ca.vinaitsolution.vinasign.session.application.ServicesSession;
import ca.vinaitsolution.vinasign.session.application.TexteConsentement;
import ca.vinaitsolution.vinasign.session.application.port.Consentements;
import ca.vinaitsolution.vinasign.session.application.port.LiensSignature;
import ca.vinaitsolution.vinasign.session.application.port.SessionsSignataire;
import ca.vinaitsolution.vinasign.session.infrastructure.web.FiltreSessionSignataire;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@EnableConfigurationProperties({SessionConfiguration.SessionProprietes.class, SessionConfiguration.AttestationProprietes.class})
public class SessionConfiguration {

    /** Durée de vie d'un lien (bornée par l'échéance de l'enveloppe), d'une session, nombre d'ouvertures d'un lien. */
    @Validated
    @ConfigurationProperties("vinasign.session")
    public record SessionProprietes(@NotNull Duration dureeLien, @NotNull Duration dureeSession, @Min(1) int ouverturesMax,
                                    @NotNull Duration dureeCode, String cleCodes, @NotNull Duration dureeIntegree,
                                    @jakarta.validation.constraints.NotBlank String urlSignataire, @NotNull Duration frequenceRelance,
                                    @Min(0) int relancesMax) {

        @Override
        public String toString() {
            return "SessionProprietes[dureeLien=" + dureeLien + ", dureeSession=" + dureeSession + "]";
        }
    }

    /** Attestation de l'utilisateur de l'application (VS-044). */
    @Validated
    @ConfigurationProperties("vinasign.session.attestation")
    public record AttestationProprietes(@jakarta.validation.constraints.NotBlank String niveauParDefaut, @Min(1) int rangRenforce,
                                        @jakarta.validation.constraints.NotEmpty java.util.Set<String> methodesFortes, @NotNull Duration fraicheur) {
    }

    @Bean
    ServicesSession servicesSession(GenerateurIdentifiant identifiants, SecureRandom alea, Clock horloge, SessionProprietes p) {
        return new ServicesSession(identifiants, alea, horloge, new ServicesSession.Reglages(p.dureeLien(), p.dureeSession(), p.ouverturesMax()));
    }

    @Bean
    TexteConsentement texteConsentement(Referentiels referentiels, VinaSignProprietes vinasign) {
        return new TexteConsentement(referentiels, new CodeLangue(vinasign.languePrincipale()));
    }

    @Bean
    EmettreLiens emettreLiens(LiensSignature liens, LectureEnveloppes enveloppes, PublicationEvenements publication, ServicesSession services) {
        return new EmettreLiens(liens, enveloppes, publication, services);
    }

    @Bean
    ca.vinaitsolution.vinasign.session.application.ConsommerLiens consommerLiens(LiensSignature liens, Clock horloge) {
        return new ca.vinaitsolution.vinasign.session.application.ConsommerLiens(liens, horloge);
    }

    /** Avant de signer : consentement à la version en vigueur (VS-041), puis niveau d'authentification exigé (VS-042, VS-043). */
    @Bean
    ca.vinaitsolution.vinasign.commun.application.port.PreconditionsSignature preconditionsSignature(Consentir consentir, LectureEnveloppes enveloppes,
            ca.vinaitsolution.vinasign.session.application.port.ClesAcces sessions, ExigerValeurReferentiel referentiel) {
        var consentement = new ca.vinaitsolution.vinasign.session.application.ConsentementPrealable(consentir);
        var niveau = new ca.vinaitsolution.vinasign.session.application.NiveauPrealable(enveloppes, sessions, referentiel);
        return s -> {
            consentement.exiger(s);
            niveau.exiger(s);
        };
    }

    @Bean
    RevoquerLiens revoquerLiens(LiensSignature liens, Clock horloge) {
        return new RevoquerLiens(liens, horloge);
    }

    @Bean
    OuvrirSession ouvrirSession(LiensSignature liens, SessionsSignataire sessions, LectureEnveloppes enveloppes, TexteConsentement textes,
                                Consentements consentements, ServicesSession services, OriginesIntegration origines,
                                AttestationsLiens attestations, DefisCode defis,
                                ca.vinaitsolution.vinasign.commun.application.port.LectureMarque marques) {
        return new OuvrirSession(liens, sessions, enveloppes, textes, consentements, services,
                new OuvrirSession.Integration(origines, attestations, defis, marques));
    }

    @Bean
    AncetresIntegration ancetresIntegration(LiensSignature liens, LectureEnveloppes enveloppes, OriginesIntegration origines,
                                            ExecutionOrganisation execution) {
        return new AncetresIntegration(liens, enveloppes, origines, execution);
    }

    /** Clés d'accès (VS-043) : partie de confiance et origine de l'interface du signataire, niveau accordé. */
    @Bean
    ca.vinaitsolution.vinasign.session.application.CleAccesSignataire cleAccesSignataire(
            ca.vinaitsolution.vinasign.commun.application.port.SignataireCourant signataire, LectureEnveloppes enveloppes,
            ca.vinaitsolution.vinasign.session.application.port.ClesAcces cles, ServicesSession services,
            @org.springframework.beans.factory.annotation.Value("${vinasign.session.cles-acces.rp-id:localhost}") String rpId,
            @org.springframework.beans.factory.annotation.Value("${vinasign.session.cles-acces.origine:${vinasign.session.url-signataire}}") String origine,
            @org.springframework.beans.factory.annotation.Value("${vinasign.session.cles-acces.niveau:renforce}") String niveau) {
        return new ca.vinaitsolution.vinasign.session.application.CleAccesSignataire(new ca.vinaitsolution.vinasign.session.application.CleAccesSignataire
                .Sources(signataire, enveloppes, cles, new ca.vinaitsolution.vinasign.session.infrastructure.webauthn.VerificationWebAuthn4j()), services,
                new ca.vinaitsolution.vinasign.session.application.CleAccesSignataire.Reglages(rpId, origine.replaceAll("/+$", ""), niveau,
                        java.time.Duration.ofMinutes(5)));
    }

    @Bean
    Consentir consentir(Consentements consentements, LectureEnveloppes enveloppes, TexteConsentement textes, ServicesSession services) {
        return new Consentir(consentements, enveloppes, textes, services);
    }

    @Bean
    AuthentifierParCode authentifierParCode(DefisCode defis, ExigerValeurReferentiel referentiel, PublicationEvenements publication,
                                            ServicesSession services, SessionProprietes p) {
        return new AuthentifierParCode(defis, new EmpreinteHmac(p.cleCodes()), referentiel, publication, services, p.dureeCode());
    }

    @Bean
    OuvrirSessionIntegree ouvrirSessionIntegree(LiensSignature liens, LectureEnveloppes enveloppes, ContexteAccesCourant acces,
                                                ExigerValeurReferentiel referentiel, ServicesSession services, SessionProprietes p,
                                                AttestationProprietes a, VerificateurAttestations verificateur, AttestationsLiens retenues) {
        return new OuvrirSessionIntegree(liens, enveloppes, acces, referentiel, services, p.dureeIntegree(), p.urlSignataire(),
                new OuvrirSessionIntegree.Attestations(verificateur, new ExigenceAttestation(a.rangRenforce(), a.methodesFortes(), a.fraicheur()),
                        retenues, a.niveauParDefaut()));
    }

    @Bean
    RelancerSignataires relancerSignataires(ca.vinaitsolution.vinasign.session.application.port.RelancesDues dues, EmettreLiens liens,
                                            ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation execution, ServicesSession services,
                                            SessionProprietes p) {
        return new RelancerSignataires(dues, liens, execution, services, p.frequenceRelance(), p.relancesMax());
    }

    @Bean
    RelancesPlanifiees relancesPlanifiees(RelancerSignataires relances) {
        return new RelancesPlanifiees(relances);
    }

    /** Relances régulières (VS-082). */
    public static class RelancesPlanifiees {

        private final RelancerSignataires relances;

        RelancesPlanifiees(RelancerSignataires relances) {
            this.relances = relances;
        }

        @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${vinasign.session.intervalle-relances:PT1H}",
                initialDelayString = "${vinasign.session.intervalle-relances:PT1H}")
        public void relancer() {
            relances.executer();
        }
    }

    @Bean
    FiltreSessionSignataire filtreSessionSignataire(SessionsSignataire sessions, Clock horloge) {
        return new FiltreSessionSignataire(sessions, horloge);
    }

    /** Le filtre est placé dans la chaîne de sécurité seulement, pas aussi dans la chaîne des servlets. */
    @Bean
    FilterRegistrationBean<FiltreSessionSignataire> sessionHorsServlets(FiltreSessionSignataire filtre) {
        FilterRegistrationBean<FiltreSessionSignataire> inscription = new FilterRegistrationBean<>(filtre);
        inscription.setEnabled(false);
        return inscription;
    }

    /** Fournisseur de vérification d'identité (VS-131) : « aucun » par défaut ; « generique » : adresse et secret de l'environnement. */
    @Bean
    ca.vinaitsolution.vinasign.session.application.port.FournisseurIdentite fournisseurIdentite(
            @org.springframework.beans.factory.annotation.Value("${vinasign.session.verification-identite.fournisseur:aucun}") String fournisseur,
            @org.springframework.beans.factory.annotation.Value("${vinasign.session.verification-identite.url:}") String url,
            @org.springframework.beans.factory.annotation.Value("${vinasign.session.verification-identite.secret:}") String secret,
            com.fasterxml.jackson.databind.ObjectMapper json) {
        return "generique".equals(fournisseur)
                ? new ca.vinaitsolution.vinasign.session.infrastructure.identite.FournisseurIdentiteGenerique(fournisseur, url, secret, json)
                : new ca.vinaitsolution.vinasign.session.infrastructure.identite.FournisseurIdentiteAucun();
    }

    @Bean
    ca.vinaitsolution.vinasign.session.application.VerifierIdentite verifierIdentite(
            ca.vinaitsolution.vinasign.commun.application.port.SignataireCourant signataire, LectureEnveloppes enveloppes,
            ca.vinaitsolution.vinasign.session.application.port.VerificationsIdentite verifications,
            ca.vinaitsolution.vinasign.session.application.port.ClesAcces sessions, ExecutionOrganisation execution,
            ca.vinaitsolution.vinasign.session.application.port.FournisseurIdentite fournisseur, ServicesSession services,
            @org.springframework.beans.factory.annotation.Value("${vinasign.session.verification-identite.niveau:identite-verifiee}") String niveau) {
        return new ca.vinaitsolution.vinasign.session.application.VerifierIdentite(
                new ca.vinaitsolution.vinasign.session.application.VerifierIdentite.Sources(signataire, enveloppes, verifications, sessions, execution),
                fournisseur, services, niveau);
    }
}
