package ca.vinaitsolution.vinasign.enveloppe.infrastructure;

import ca.vinaitsolution.vinasign.commun.application.port.ActeurCourant;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.GenerateurIdentifiant;
import ca.vinaitsolution.vinasign.commun.application.port.PlanOrganisations;
import ca.vinaitsolution.vinasign.commun.application.port.PublicationEvenements;
import ca.vinaitsolution.vinasign.enveloppe.application.CreerEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.DefinirSignataires;
import ca.vinaitsolution.vinasign.enveloppe.application.DeposerDocument;
import ca.vinaitsolution.vinasign.enveloppe.application.ExpirerEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.PiloterEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.ServicesEnveloppe;
import ca.vinaitsolution.vinasign.enveloppe.application.port.AnalyseAntivirus;
import ca.vinaitsolution.vinasign.enveloppe.application.port.ConversionPdfA;
import ca.vinaitsolution.vinasign.enveloppe.application.port.DepotsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.EcheancesEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.Enveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.InspectionPdf;
import ca.vinaitsolution.vinasign.enveloppe.application.port.JournalEnveloppes;
import ca.vinaitsolution.vinasign.enveloppe.application.port.StockageDocuments;
import ca.vinaitsolution.vinasign.enveloppe.application.port.VersionsDocuments;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle.AntivirusClamd;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle.ConversionGotenberg;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.controle.InspectionPdfBox;
import ca.vinaitsolution.vinasign.enveloppe.infrastructure.stockage.StockageAzureBlob;
import ca.vinaitsolution.vinasign.referentiel.application.ExigerValeurReferentiel;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableConfigurationProperties(EnveloppeProprietes.class)
public class EnveloppeConfiguration {

    static final String CHEMIN_DEPOT = "/api/v1/depots/";

    @Bean
    ServicesEnveloppe servicesEnveloppe(Enveloppes enveloppes, JournalEnveloppes journal, ContexteAccesCourant acces, ActeurCourant acteur,
                                        GenerateurIdentifiant identifiants, Clock horloge, PublicationEvenements publication) {
        return new ServicesEnveloppe(enveloppes, journal, acces, acteur, identifiants, horloge, publication);
    }

    @Bean
    CreerEnveloppe creerEnveloppe(ServicesEnveloppe services, ExigerValeurReferentiel referentiel, PlanOrganisations plans) {
        return new CreerEnveloppe(services, referentiel, plans);
    }

    @Bean
    DefinirSignataires definirSignataires(ServicesEnveloppe services, ExigerValeurReferentiel referentiel, VersionsDocuments versions) {
        return new DefinirSignataires(services, referentiel, versions);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.ListerEnveloppes listerEnveloppes(ServicesEnveloppe services,
                                                                                       ca.vinaitsolution.vinasign.enveloppe.application.port.ListeEnveloppes liste) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.ListerEnveloppes(services, liste);
    }

    @Bean
    PiloterEnveloppe piloterEnveloppe(ServicesEnveloppe services, VersionsDocuments versions,
                                      ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe champs, ExigerValeurReferentiel referentiel) {
        return new PiloterEnveloppe(services, versions, champs, referentiel);
    }

    @Bean
    DeposerDocument deposerDocument(ServicesEnveloppe services, DepotsDocuments depots, StockageDocuments stockage, VersionsDocuments versions,
                                    InspectionPdf pdf, AnalyseAntivirus antivirus, ConversionPdfA conversion, EnveloppeProprietes p,
                                    SecureRandom alea) {
        String prefixe = p.urlPublique().replaceAll("/+$", "") + CHEMIN_DEPOT;
        return new DeposerDocument(services, depots, stockage, versions, new DeposerDocument.Controles(pdf, antivirus, conversion),
                new DeposerDocument.Reglages(p.validiteDepot(), p.tailleMax(), prefixe), alea);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.DefinirChamps definirChamps(ServicesEnveloppe services,
            ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe champs, VersionsDocuments versions, ExigerValeurReferentiel referentiel) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.DefinirChamps(services, champs, versions, referentiel);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.AgirCommeSignataire agirCommeSignataire(ServicesEnveloppe services,
            ca.vinaitsolution.vinasign.commun.application.port.SignataireCourant signataire,
            ca.vinaitsolution.vinasign.commun.application.port.PreconditionsSignature preconditions,
            ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe champs, VersionsDocuments versions, DeposerDocument documents,
            StockageDocuments stockage, ca.vinaitsolution.vinasign.enveloppe.application.port.SignaturesSignataires signatures,
            ExigerValeurReferentiel referentiel) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.AgirCommeSignataire(services, signataire, preconditions,
                new ca.vinaitsolution.vinasign.enveloppe.application.AgirCommeSignataire.Depots(champs, versions, documents, stockage, signatures,
                        referentiel));
    }

    @Bean
    ExpirerEnveloppes expirerEnveloppes(EcheancesEnveloppes echeances, GenerateurIdentifiant identifiants, Clock horloge, EnveloppeProprietes p) {
        return new ExpirerEnveloppes(echeances, identifiants, horloge, p.expiration().lot());
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.FinaliserEnveloppes finaliserEnveloppes(
            ca.vinaitsolution.vinasign.enveloppe.application.port.EnveloppesAFinaliser aFinaliser,
            ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation execution, ServicesEnveloppe services, VersionsDocuments versions,
            StockageDocuments stockage, ca.vinaitsolution.vinasign.enveloppe.application.port.ChampsEnveloppe champs,
            ca.vinaitsolution.vinasign.enveloppe.application.port.LectureSaisies saisies,
            ca.vinaitsolution.vinasign.enveloppe.application.port.CompositionDocument composition,
            ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments scellement, ExigerValeurReferentiel referentiel,
            @org.springframework.beans.factory.annotation.Value("${vinasign.enveloppes.finalisation.lot:50}") int lot) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.FinaliserEnveloppes(aFinaliser, execution, services,
                new ca.vinaitsolution.vinasign.enveloppe.application.FinaliserEnveloppes.Documents(versions, stockage, champs, saisies, composition,
                        scellement), referentiel, lot);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.TelechargerDocument telechargerDocument(ServicesEnveloppe services, VersionsDocuments versions,
                                                                                         StockageDocuments stockage) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.TelechargerDocument(services, versions, stockage);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.VerifierJournal verifierJournal(ServicesEnveloppe services,
            ca.vinaitsolution.vinasign.enveloppe.application.port.JournalPreuve journal) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.VerifierJournal(services, journal);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.AncrerJournal ancrerJournal(ca.vinaitsolution.vinasign.enveloppe.application.port.JournalPreuve journal,
            ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments scellement, GenerateurIdentifiant identifiants, Clock horloge,
            @org.springframework.beans.factory.annotation.Value("${vinasign.preuve.ancrage.marge:PT1M}") java.time.Duration marge) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.AncrerJournal(journal, scellement, identifiants, horloge, marge);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.ProduireCertificat produireCertificat(ServicesEnveloppe services, VersionsDocuments versions,
            ca.vinaitsolution.vinasign.enveloppe.application.port.JournalEnveloppes journal,
            ca.vinaitsolution.vinasign.enveloppe.application.VerifierJournal integrite,
            ca.vinaitsolution.vinasign.commun.application.port.LecturePreuvesSignataires signataires,
            ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel referentiels,
            ca.vinaitsolution.vinasign.enveloppe.application.port.RenduCertificat rendu, ConversionPdfA conversion,
            ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments scellement,
            @org.springframework.beans.factory.annotation.Value("${vinasign.langue-principale:fr}") String langue) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.ProduireCertificat(services,
                new ca.vinaitsolution.vinasign.enveloppe.application.ProduireCertificat.Sources(versions, journal, integrite, signataires),
                new ca.vinaitsolution.vinasign.enveloppe.application.ProduireCertificat.Fabrication(referentiels, rendu, conversion, scellement,
                        new ca.vinaitsolution.vinasign.commun.domain.CodeLangue(langue)));
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.ExporterPreuve exporterPreuve(ServicesEnveloppe services, VersionsDocuments versions,
            StockageDocuments stockage, ca.vinaitsolution.vinasign.enveloppe.application.port.JournalPreuve journal,
            ca.vinaitsolution.vinasign.enveloppe.application.ProduireCertificat certificat,
            ca.vinaitsolution.vinasign.commun.application.port.ScellementDocuments scellement,
            ca.vinaitsolution.vinasign.enveloppe.application.port.EmballagePreuve emballage,
            ca.vinaitsolution.vinasign.enveloppe.application.port.ArchivesDocuments archives) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.ExporterPreuve(services,
                new ca.vinaitsolution.vinasign.enveloppe.application.ExporterPreuve.Contenus(versions, stockage, journal, certificat, archives), scellement,
                emballage);
    }

    @Bean
    AncragePlanifie ancragePlanifie(ca.vinaitsolution.vinasign.enveloppe.application.AncrerJournal ancrage) {
        return new AncragePlanifie(ancrage);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.PlacerParAncres placerParAncres(ServicesEnveloppe services, VersionsDocuments versions,
            StockageDocuments stockage, ca.vinaitsolution.vinasign.enveloppe.application.port.LocalisationAncres localisation,
            ca.vinaitsolution.vinasign.enveloppe.application.DefinirChamps champs) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.PlacerParAncres(services,
                new ca.vinaitsolution.vinasign.enveloppe.application.PlacerParAncres.Document(versions, stockage, localisation), champs);
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.ReleverConsommation releverConsommation(ServicesEnveloppe services,
            ca.vinaitsolution.vinasign.enveloppe.application.port.JournalConsommation journal,
            @org.springframework.beans.factory.annotation.Value("${vinasign.fuseau:America/Toronto}") String fuseau) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.ReleverConsommation(services, journal, java.time.ZoneId.of(fuseau));
    }

    @Bean
    ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives renouvelerArchives(
            ca.vinaitsolution.vinasign.enveloppe.application.port.ArchivesDocuments archives, VersionsDocuments versions, StockageDocuments stockage,
            ca.vinaitsolution.vinasign.commun.application.port.ArchivageLongTerme archivage, ServicesEnveloppe services,
            ca.vinaitsolution.vinasign.commun.application.port.ExecutionOrganisation execution,
            ca.vinaitsolution.vinasign.enveloppe.application.port.AlertesArchive alertes,
            @org.springframework.beans.factory.annotation.Value("${vinasign.archivage.marge:P90D}") java.time.Duration marge,
            @org.springframework.beans.factory.annotation.Value("${vinasign.archivage.lot:50}") int lot) {
        return new ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives(
                new ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives.Documents(archives, versions, stockage, archivage), services,
                new ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives.Exploitation(execution, alertes, marge, lot));
    }

    @Bean
    ArchivagePlanifie archivagePlanifie(ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives renouvellement) {
        return new ArchivagePlanifie(renouvellement);
    }

    @Bean
    FinalisationPlanifiee finalisationPlanifiee(ca.vinaitsolution.vinasign.enveloppe.application.FinaliserEnveloppes finalisation) {
        return new FinalisationPlanifiee(finalisation);
    }

    @Bean
    ExpirationPlanifiee expirationPlanifiee(ExpirerEnveloppes expiration) {
        return new ExpirationPlanifiee(expiration);
    }

    /** Conteneur privé : chaîne de connexion sur un poste ou en test (Azurite), identité gérée en exploitation. */
    @Bean
    BlobContainerClient conteneurDocuments(EnveloppeProprietes p) {
        String connexion = p.stockage().connexion();
        BlobServiceClientBuilder b = connexion != null && !connexion.isBlank() ? new BlobServiceClientBuilder().connectionString(connexion)
                : ca.vinaitsolution.vinasign.commun.infrastructure.IdentiteAzure.stockage(p.stockage().pointDeTerminaison());
        BlobContainerClient conteneur = b.buildClient().getBlobContainerClient(p.stockage().conteneur());
        if (p.stockage().creerConteneur()) {
            conteneur.createIfNotExists();
        }
        return conteneur;
    }

    @Bean
    StockageDocuments stockageDocuments(BlobContainerClient conteneur) {
        return new StockageAzureBlob(conteneur);
    }

    @Bean
    AnalyseAntivirus analyseAntivirus(EnveloppeProprietes p) {
        return new AntivirusClamd(p.antivirus().hote(), p.antivirus().port(), p.antivirus().delai());
    }

    @Bean
    ConversionPdfA conversionPdfA(EnveloppeProprietes p) {
        return new ConversionGotenberg(p.conversion().url(), p.conversion().delai());
    }

    @Bean
    InspectionPdf inspectionPdf(EnveloppeProprietes p) {
        return new InspectionPdfBox(p.tailleMax() * 4);
    }

    /** Expiration à échéance ; sans verrou : la fonction n'expire qu'une fois une enveloppe, quelle que soit la réplique. */
    public static class ExpirationPlanifiee {

        private final ExpirerEnveloppes expiration;

        ExpirationPlanifiee(ExpirerEnveloppes expiration) {
            this.expiration = expiration;
        }

        @Scheduled(fixedDelayString = "${vinasign.enveloppes.expiration.intervalle:PT1M}", initialDelayString = "${vinasign.enveloppes.expiration.intervalle:PT1M}")
        public void expirer() {
            expiration.executer();
        }
    }

    /**
     * Finalisation des enveloppes entièrement signées (VS-070). Plusieurs répliques peuvent la mener en même temps : la
     * version finale est unique par enveloppe, la seconde tentative échoue sans rien conserver.
     */
    public static class FinalisationPlanifiee {

        private final ca.vinaitsolution.vinasign.enveloppe.application.FinaliserEnveloppes finalisation;

        FinalisationPlanifiee(ca.vinaitsolution.vinasign.enveloppe.application.FinaliserEnveloppes finalisation) {
            this.finalisation = finalisation;
        }

        @Scheduled(fixedDelayString = "${vinasign.enveloppes.finalisation.intervalle:PT15S}",
                initialDelayString = "${vinasign.enveloppes.finalisation.intervalle:PT15S}")
        public void finaliser() {
            finalisation.executer();
        }
    }

    /** Ancrage horaire du journal de preuve (VS-071) ; une seconde réplique ne trouve qu'une fenêtre vide ou déjà ancrée. */
    public static class AncragePlanifie {

        private final ca.vinaitsolution.vinasign.enveloppe.application.AncrerJournal ancrage;

        AncragePlanifie(ca.vinaitsolution.vinasign.enveloppe.application.AncrerJournal ancrage) {
            this.ancrage = ancrage;
        }

        @Scheduled(fixedDelayString = "${vinasign.preuve.ancrage.intervalle:PT1H}", initialDelayString = "${vinasign.preuve.ancrage.intervalle:PT1H}")
        public void ancrer() {
            ancrage.executer();
        }
    }

    /** Renouvellement des horodatages d'archive (VS-132) ; une seconde réplique ne trouve que des états déjà renouvelés. */
    public static class ArchivagePlanifie {

        private final ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives renouvellement;

        ArchivagePlanifie(ca.vinaitsolution.vinasign.enveloppe.application.RenouvelerArchives renouvellement) {
            this.renouvellement = renouvellement;
        }

        @Scheduled(fixedDelayString = "${vinasign.archivage.intervalle:PT6H}", initialDelayString = "${vinasign.archivage.intervalle:PT6H}")
        public void renouveler() {
            renouvellement.executer();
        }
    }
}
