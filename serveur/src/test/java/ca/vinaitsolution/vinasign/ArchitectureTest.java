package ca.vinaitsolution.vinasign;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/** Règles de dépendance et règle « tout est dynamique », vérifiées à chaque construction plutôt qu'en revue. */
class ArchitectureTest {

    private static final JavaClasses CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("ca.vinaitsolution.vinasign");

    @Test
    void le_domaine_ne_depend_que_de_java_et_du_domaine() {
        classes().that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..")
                .because("le domaine ne dépend de rien : ni framework, ni base, ni HTTP").check(CODE);
    }

    @Test
    void l_application_ignore_l_infrastructure_et_les_frameworks() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "org.springframework..", "jakarta.persistence..",
                        "com.fasterxml..")
                .because("les dépendances pointent vers l'intérieur").check(CODE);
    }

    @Test
    void les_controleurs_vivent_dans_l_infrastructure_web() {
        classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..infrastructure.web..").check(CODE);
    }

    @Test
    void aucune_liste_metier_n_est_un_enum() {
        noClasses().that().areEnums().should().resideOutsideOfPackage("..domain..")
                .because("une liste métier est un référentiel en base ; un enum n'est permis que pour un invariant du domaine")
                .allowEmptyShould(true)
                .check(CODE);
    }

    @Test
    void seule_l_infrastructure_journalise() {
        noClasses().that().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("org.slf4j..", "java.util.logging..", "ch.qos.logback..")
                .because("le domaine et les cas d'utilisation ne journalisent pas : ils manipulent les renseignements "
                        + "personnels ; l'infrastructure journalise par le format masqué (VS-004)")
                .check(CODE);
    }

    @Test
    void personne_n_ecrit_sur_la_sortie_standard() {
        noClasses().should().accessField(System.class, "out").orShould().accessField(System.class, "err")
                .orShould().callMethod(Throwable.class, "printStackTrace")
                .because("toute sortie passe par le journal structuré et masqué (VS-004)").check(CODE);
    }

    @Test
    void les_contextes_ne_se_lisent_pas_par_leur_infrastructure() {
        slices().matching("ca.vinaitsolution.vinasign.(*)..").should().notDependOnEachOther()
                .ignoreDependency(com.tngtech.archunit.base.DescribedPredicate.alwaysTrue(),
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage(
                                "ca.vinaitsolution.vinasign.commun..", "ca.vinaitsolution.vinasign.referentiel.application..",
                                "ca.vinaitsolution.vinasign.referentiel.domain.."))
                .because("un contexte en lit un autre par un port ou un cas d'utilisation, jamais par son infrastructure")
                .check(CODE);
    }
}
