package ca.vinaitsolution.vinasign.integration.infrastructure.mcp;

import ca.vinaitsolution.vinasign.commun.application.dto.DossierEnveloppe;
import ca.vinaitsolution.vinasign.commun.application.port.LectureEnveloppes;
import ca.vinaitsolution.vinasign.commun.domain.CodeLangue;
import ca.vinaitsolution.vinasign.referentiel.application.ConsulterReferentiel;
import ca.vinaitsolution.vinasign.referentiel.domain.CodeReferentiel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Outils MCP de VinaSign (VS-094), en lecture seule. Une enveloppe d'une autre application est « introuvable », comme par
 * l'API ; aucun renseignement personnel des signataires (nom, courriel, téléphone) n'est rendu à l'assistant.
 */
public final class OutilsVinaSign {

    private OutilsVinaSign() {
    }

    public static OutilMcp lireEnveloppe(LectureEnveloppes enveloppes) {
        return outil("lire_enveloppe", "Statut d'une enveloppe",
                "Donne le statut d'une enveloppe créée par cette application : référence, mode, échéance, et pour chaque signataire son ordre, "
                        + "son statut et s'il peut agir. Aucun renseignement personnel.",
                OutilMcp.schemaChaine("enveloppeId", "Identifiant de l'enveloppe (UUID), rendu à sa création."), (application, args) -> {
                    DossierEnveloppe d = enveloppes.dossier(OutilMcp.identifiant(args, "enveloppeId")).filter(x -> x.application().equals(application))
                            .orElseThrow(() -> new OutilMcp.ArgumentInvalide("Enveloppe introuvable pour cette application."));
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("enveloppeId", d.id().toString());
                    r.put("referenceExterne", d.referenceExterne());
                    r.put("mode", d.mode());
                    r.put("statut", d.statut());
                    r.put("expireLe", d.expireLe().toString());
                    r.put("signataires", d.signataires().stream().map(s -> Map.<String, Object>of("ordre", s.ordre(), "statut", s.statut(),
                            "peutAgir", s.peutAgir())).toList());
                    return r;
                });
    }

    public static OutilMcp lireReferentiel(ConsulterReferentiel referentiels, CodeLangue langue) {
        return outil("lire_referentiel", "Valeurs d'une liste de référence",
                "Donne les valeurs d'un référentiel de VinaSign (ex. statut-enveloppe, motif-refus) : code et libellé.",
                OutilMcp.schemaChaine("code", "Code du référentiel."), (application, args) -> {
                    try {
                        var r = referentiels.executer(new CodeReferentiel(String.valueOf(args.get("code"))), langue);
                        return Map.of("code", r.code(), "libelle", r.libelle(),
                                "valeurs", r.valeurs().stream().map(v -> Map.of("code", v.code(), "libelle", v.libelle())).toList());
                    } catch (RuntimeException e) {
                        throw new OutilMcp.ArgumentInvalide("Référentiel inconnu : " + args.get("code"));
                    }
                });
    }

    private static OutilMcp outil(String nom, String titre, String description, Map<String, Object> schema,
                                  BiFunction<String, Map<String, Object>, Map<String, Object>> execution) {
        return new OutilMcp() {
            @Override
            public String nom() {
                return nom;
            }

            @Override
            public String titre() {
                return titre;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public Map<String, Object> schemaEntree() {
                return schema;
            }

            @Override
            public Map<String, Object> executer(String application, Map<String, Object> arguments) {
                return execution.apply(application, arguments);
            }
        };
    }

}
