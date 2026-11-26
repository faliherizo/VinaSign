# Scénarios BDD — VinaSign — backlog produit

*Plateforme de signature électronique opérée par Vina IT Solution.* Source des exigences : `docs/architecture/05-Backlog-et-stories.md`.

> Document généré par `bdd/outils/documenter.mjs` depuis le catalogue, les fonctionnalités Gherkin et les cas JSON ;
> ne pas le modifier à la main. Régénérer : `cd bdd && npm run documenter`.

## Synthèse

| Exigences | Éprouvées par scénario | Dont réussies à la dernière exécution | Documentaires | Écarts déclarés | Sans objet |
|---|---|---|---|---|---|
| 78 | 19 | 0 | 11 | 0 | 0 |

## Matrice des exigences

| Exigence | Libellé | Caractère | Vérification | Scénarios |
|---|---|---|---|---|
| `VS-R01` | Toute liste métier vient d'un référentiel administrable : aucune liste, aucun identifiant ni aucune option figés dans le code ou l'interface | Obligatoire (règle de projet) | Scénario BDD exécuté | — [Chaque référentiel annoncé par l'API se consulte dans la langue demandée](#chaque-referentiel-annonce-par-lapi-se-consulte-dans-la-langue-demandee)<br>— [Le portail tire son choix de langue du référentiel et change de langue sans rechargement](#le-portail-tire-son-choix-de-langue-du-referentiel-et-change-de-langue-sans-rechargement)<br>— [Le portail et l'écran du signataire restent accessibles à toutes les largeurs](#le-portail-et-lecran-du-signataire-restent-accessibles-a-toutes-les-largeurs) |
| `VS-001` | En tant qu'équipe, je veux le dépôt `vinasign` structuré par contexte (`domain/application/infrastructure`) afin de livrer des modules indépendants | Must — pilote interne | Documentaire | Règles de dépendance et découpage vérifiés à chaque construction. |
| `VS-002` | En tant qu'équipe, je veux une CI qui compile, teste, mesure et analyse | Must — pilote interne | Documentaire | Chaîne d'intégration continue : portes de couverture par classe et agrégée, domaine à 100 %, cliquet, mutation, Trivy, SBOM. |
| `VS-003` | En tant qu'exploitant, je veux des environnements reproductibles et des secrets externalisés | Must — pilote interne | Documentaire | Infrastructure en code éprouvée sans compte infonuagique (terraform test, lint et rendu Helm). Reste : plan réel. |
| `VS-004` | En tant qu'exploitant, je veux des journaux structurés, des métriques et des traces | Must — pilote interne | Documentaire | Journal structuré et masqué, éprouvé sur l'application démarrée ; ArchUnit : seule l'infrastructure journalise. |
| `VS-005` | En tant que client, je veux que la base isole les organisations | Must — pilote interne | Documentaire | RLS éprouvée sur toutes les tables isolées, dont l'absence de toute ligne sans contexte. |
| `VS-010` | En tant qu'administrateur de la plateforme, je veux créer, suspendre et réactiver une organisation | Must — pilote interne | Scénario BDD exécuté | — [Organisation, administrateur invité, rôles et MFA, suspension et réactivation](#organisation-administrateur-invite-roles-et-mfa-suspension-et-reactivation) |
| `VS-011` | En tant qu'administrateur d'organisation, je veux enregistrer une application cliente (environnement, JWKS, scopes, origines d'intégration) | Must — pilote interne | Scénario BDD exécuté | — [Application cliente enregistrée, rotation de ses clés et révocation immédiate](#application-cliente-enregistree-rotation-de-ses-cles-et-revocation-immediate) |
| `VS-012` | En tant qu'application cliente, je veux obtenir un jeton par client credentials (`private_key_jwt`) | Must — pilote interne | Scénario BDD exécuté | — [L'application obtient un jeton par client credentials et private_key_jwt](#lapplication-obtient-un-jeton-par-client-credentials-et-private_key_jwt) |
| `VS-013` | En tant qu'administrateur d'organisation, je veux révoquer ou faire tourner les clés d'une application | Must — pilote interne | Scénario BDD exécuté | — [Application cliente enregistrée, rotation de ses clés et révocation immédiate](#application-cliente-enregistree-rotation-de-ses-cles-et-revocation-immediate) |
| `VS-014` | En tant qu'administrateur d'organisation, je veux gérer les utilisateurs du portail (OIDC, rôles) | Must — pilote interne | Scénario BDD exécuté | — [Organisation, administrateur invité, rôles et MFA, suspension et réactivation](#organisation-administrateur-invite-roles-et-mfa-suspension-et-reactivation)<br>— [L'écran de connexion du portail est accessible à toutes les largeurs](#lecran-de-connexion-du-portail-est-accessible-a-toutes-les-largeurs) |
| `VS-015` | En tant qu'exploitant, je veux limiter le débit et appliquer des quotas par application | Must — pilote interne | Documentaire | 429 avec Retry-After au-delà du plan, métriques par application : éprouvé sur la vraie pile (le serveur de la campagne BDD relève le débit par adresse). |
| `VS-020` | En tant qu'application cliente, je veux créer une enveloppe avec une référence externe et une clé d'idempotence | Must — pilote interne | Scénario BDD exécuté | — [Parcours complet d'une enveloppe en mode document](#parcours-complet-dune-enveloppe-en-mode-document) |
| `VS-021` | En tant qu'application cliente, je veux déposer un PDF par une URL présignée | Must — pilote interne | Scénario BDD exécuté | — [Parcours complet d'une enveloppe en mode document](#parcours-complet-dune-enveloppe-en-mode-document)<br>— [Refus explicites et annulation](#refus-explicites-et-annulation) |
| `VS-022` | En tant que système, je veux des versions immuables (original, préparé, final), chacune avec son SHA-256 | Must — pilote interne | Scénario BDD exécuté | — [Parcours complet d'une enveloppe en mode document](#parcours-complet-dune-enveloppe-en-mode-document) |
| `VS-023` | En tant que préparateur, je veux définir les signataires, leur ordre (séquentiel ou parallèle), leur langue et leur type (invité ou utilisateur de l'application) | Must — pilote interne | Scénario BDD exécuté | — [Parcours complet d'une enveloppe en mode document](#parcours-complet-dune-enveloppe-en-mode-document)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement) |
| `VS-024` | En tant que système, je veux contrôler les transitions de l'enveloppe et des signataires | Must — pilote interne | Scénario BDD exécuté | — [Parcours complet d'une enveloppe en mode document](#parcours-complet-dune-enveloppe-en-mode-document)<br>— [Refus explicites et annulation](#refus-explicites-et-annulation) |
| `VS-025` | En tant que préparateur, je veux annuler une enveloppe ; en tant que système, l'expirer à échéance | Must — pilote interne | Scénario BDD exécuté | — [Refus explicites et annulation](#refus-explicites-et-annulation) |
| `VS-030` | En tant qu'équipe, je veux des tests de caractérisation du moteur du CLM avant de le porter | Must — pilote interne | À réaliser (story du backlog) | Suite de caractérisation écrite (pyHanko, openssl, veraPDF) ; exécution en attente : mvn -Pcaracterisation test. |
| `VS-031` | En tant que système, je veux signer avec une clé du HSM (port `CleDeSignature`) | Must — pilote interne | Documentaire | Contrat CleDeSignature exécuté contre PKCS#12 et Key Vault (émulateur Lowkey Vault, vrai SDK) ; clé exportable et PKCS#12 en production refusés. |
| `VS-032` | En tant qu'organisation, je veux que le PDF final porte mon cachet PAdES-B-LTA | Must — pilote interne | À réaliser (story du backlog) | Sprint 4. Critères : Porté de `CachetX509` ; pyHanko : « VALID », niveau LTA ; un certificat renouvelé reste reconnu Origine : CLM. |
| `VS-033` | En tant que système, je veux horodater par RFC 3161 avec une autorité de repli | Must — pilote interne | Documentaire | — [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement) |
| `VS-034` | En tant qu'application cliente, je veux faire signer une empreinte sans transmettre le document | Must — pilote interne | Scénario BDD exécuté | — [Empreinte signée et horodatée, puis vérifiée publiquement](#empreinte-signee-et-horodatee-puis-verifiee-publiquement) |
| `VS-035` | En tant que système, je veux produire un PDF/A et convertir les formats bureautiques | Must — pilote interne | Documentaire | — [Un Word déposé au portail est affiché ; la préparatrice y place initiales et signature où elle veut, puis la signataire signe](#un-word-depose-au-portail-est-affiche-la-preparatrice-y-place-initiales-et-signature-ou-elle-veut-puis-la-signataire-signe) |
| `VS-036` | En tant que tiers, je veux vérifier un document signé ou une empreinte | Must — pilote interne | Scénario BDD exécuté | — [Empreinte signée et horodatée, puis vérifiée publiquement](#empreinte-signee-et-horodatee-puis-verifiee-publiquement)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement)<br>— [Préparée au portail sans placer de zone, l'enveloppe est signée à la main ; la signature figure sur le document scellé](#preparee-au-portail-sans-placer-de-zone-lenveloppe-est-signee-a-la-main-la-signature-figure-sur-le-document-scelle)<br>— [Un Word déposé au portail est affiché ; la préparatrice y place initiales et signature où elle veut, puis la signataire signe](#un-word-depose-au-portail-est-affiche-la-preparatrice-y-place-initiales-et-signature-ou-elle-veut-puis-la-signataire-signe) |
| `VS-040` | En tant qu'invité, je veux ouvrir un lien sécurisé sans compte | Must — pilote interne | Scénario BDD exécuté | — [Du courriel d'invitation au consentement et au code, puis l'annulation invalide le lien](#du-courriel-dinvitation-au-consentement-et-au-code-puis-lannulation-invalide-le-lien)<br>— [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement) |
| `VS-041` | En tant que signataire, je veux accepter un consentement explicite | Must — pilote interne | Scénario BDD exécuté | — [Du courriel d'invitation au consentement et au code, puis l'annulation invalide le lien](#du-courriel-dinvitation-au-consentement-et-au-code-puis-lannulation-invalide-le-lien)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement) |
| `VS-042` | En tant que signataire au niveau renforcé, je veux recevoir un code par courriel ou SMS | Must — pilote interne | Scénario BDD exécuté | — [Du courriel d'invitation au consentement et au code, puis l'annulation invalide le lien](#du-courriel-dinvitation-au-consentement-et-au-code-puis-lannulation-invalide-le-lien) |
| `VS-043` | En tant que signataire récurrent, je veux signer avec une clé d'accès (WebAuthn) | Must — pilote interne | À réaliser (story du backlog) | Sprint 8. Critères : Enregistrement et usage ; `amr=hwk` attesté ; repli sur le code Origine : N. |
| `VS-044` | En tant qu'application cliente, je veux attester mon utilisateur déjà authentifié | Must — pilote interne | À réaliser (story du backlog) | — [Session intégrée éphémère, à usage unique, pour l'utilisateur attesté par l'application](#session-integree-ephemere-a-usage-unique-pour-lutilisateur-atteste-par-lapplication) |
| `VS-045` | En tant que signataire, je veux refuser de signer, avec un motif facultatif | Must — pilote interne | À réaliser (story du backlog) | Sprint 8. Critères : Refus distinct d'une signature ; enveloppe `DECLINED` ; webhook `signer.declined` Origine : D1 US-055. |
| `VS-046` | En tant qu'application cliente, je veux une session intégrée éphémère pour un signataire | Must — pilote interne | Scénario BDD exécuté | — [Session intégrée éphémère, à usage unique, pour l'utilisateur attesté par l'application](#session-integree-ephemere-a-usage-unique-pour-lutilisateur-atteste-par-lapplication)<br>— [L'application intègre la session, l'utilisateur signe, l'application est avisée](#lapplication-integre-la-session-lutilisateur-signe-lapplication-est-avisee) |
| `VS-050` | En tant que signataire, je veux lire le document sur téléphone ou tablette | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape) |
| `VS-051` | En tant que signataire, je veux dessiner ma signature au doigt ou au stylet, l'effacer et recommencer | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement)<br>— [Préparée au portail sans placer de zone, l'enveloppe est signée à la main ; la signature figure sur le document scellé](#preparee-au-portail-sans-placer-de-zone-lenveloppe-est-signee-a-la-main-la-signature-figure-sur-le-document-scelle)<br>— [Un Word déposé au portail est affiché ; la préparatrice y place initiales et signature où elle veut, puis la signataire signe](#un-word-depose-au-portail-est-affiche-la-preparatrice-y-place-initiales-et-signature-ou-elle-veut-puis-la-signataire-signe) |
| `VS-052` | En tant que signataire, je veux remplir les champs qui me sont assignés | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement) |
| `VS-053` | En tant que signataire, je veux une interface accessible | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape) |
| `VS-054` | En tant que signataire, je veux l'interface en français ou en anglais | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape) |
| `VS-055` | En tant qu'application cliente, je veux intégrer la signature dans ma page (`@vinasign/embed`) | Must — pilote interne | À réaliser (story du backlog) | — [L'application intègre la session, l'utilisateur signe, l'application est avisée](#lapplication-integre-la-session-lutilisateur-signe-lapplication-est-avisee) |
| `VS-070` | En tant que système, je veux finaliser le document de façon idempotente | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement)<br>— [Préparée au portail sans placer de zone, l'enveloppe est signée à la main ; la signature figure sur le document scellé](#preparee-au-portail-sans-placer-de-zone-lenveloppe-est-signee-a-la-main-la-signature-figure-sur-le-document-scelle)<br>— [Un Word déposé au portail est affiché ; la préparatrice y place initiales et signature où elle veut, puis la signataire signe](#un-word-depose-au-portail-est-affiche-la-preparatrice-y-place-initiales-et-signature-ou-elle-veut-puis-la-signataire-signe) |
| `VS-071` | En tant qu'auditeur, je veux un journal de preuve inaltérable | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape) |
| `VS-072` | En tant qu'expéditeur, je veux un certificat de réalisation | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement) |
| `VS-073` | En tant qu'auditeur, je veux exporter le dossier de preuve | Must — pilote interne | À réaliser (story du backlog) | — [Du lien reçu par courriel à la signature au clavier, accessible à chaque étape](#du-lien-recu-par-courriel-a-la-signature-au-clavier-accessible-a-chaque-etape)<br>— [Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement](#deux-signatures-manuscrites-dans-lordre-puis-le-document-scelle-et-horodate-se-verifie-publiquement) |
| `VS-080` | En tant que signataire, je veux recevoir l'invitation et la confirmation par courriel | Must — pilote interne | Scénario BDD exécuté | — [Du courriel d'invitation au consentement et au code, puis l'annulation invalide le lien](#du-courriel-dinvitation-au-consentement-et-au-code-puis-lannulation-invalide-le-lien) |
| `VS-081` | En tant que signataire, je veux recevoir mon code par SMS | Must — pilote interne | Documentaire | Téléphone chiffré (AES-GCM), code par SMS via la boîte d'envoi et le simulateur ; simulateur refusé en production. |
| `VS-082` | En tant qu'expéditeur, je veux des relances et une échéance | Must — pilote interne | Documentaire | Relance après la fréquence, lien réémis (ancien révoqué), arrêt au maximum (plafond 10) ; aucun courriel pour une enveloppe annulée. |
| `VS-090` | En tant qu'intégrateur, je veux un contrat OpenAPI v1 et des erreurs RFC 9457 | Must — pilote interne | À réaliser (story du backlog) | Sprint 7. Critères : Publié ; tests de contrat dans la CI ; erreurs typées Origine : D2 E15-01. |
| `VS-091` | En tant qu'application cliente, je veux des webhooks signés et fiables | Must — pilote interne | À réaliser (story du backlog) | — [Déclarer, consulter et retirer une destination de webhooks, gardée contre la SSRF](#declarer-consulter-et-retirer-une-destination-de-webhooks-gardee-contre-la-ssrf) |
| `VS-092` | En tant que développeur Java, je veux un SDK et un module d'autoconfiguration Spring Boot | Must — pilote interne | À réaliser (story du backlog) | Sprint 8. Critères : Java 17 et 21 ; client généré ; vérification des webhooks ; aide à l'empreinte (canonicalisation, PDF externe avec PDFBox) Origine : D2 E15-10. |
| `VS-093` | En tant que développeur TypeScript, je veux `@vinasign/client` et `@vinasign/embed` | Must — pilote interne | À réaliser (story du backlog) | Sprint 8. Critères : Node 20 et plus ; navigateur sans dépendance ; exemples React et Angular Origine : D2 E15-09. |
| `VS-094` | En tant qu'utilisateur d'un assistant d'IA, je veux consulter l'état de mes enveloppes par MCP | Must — pilote interne | À réaliser (story du backlog) | Sprint 8. Critères : Porté de `ServeurMcp` ; lecture seule ; validé par le client de référence Origine : CLM. |
| `VS-095` | En tant qu'intégrateur, je veux un bac à sable | Must — pilote interne | À réaliser (story du backlog) | — [L'intégrateur réinitialise son bac à sable](#lintegrateur-reinitialise-son-bac-a-sable) |
| `VS-100` | En tant que professionnel du SAD, je veux signer et faire cosigner ma note par VinaSign | Must — pilote interne | À réaliser (story du backlog) | Sprint 9. Critères : US-408 de bout en bout : empreinte, assertion avec TOTP, cosignature ordonnée, CMS conservé avec la note, BDD du SAD au vert Origine : N. |
| `VS-101` | En tant qu'usager de Télésoin, je veux signer mon consentement sur la tablette | Must — pilote interne | À réaliser (story du backlog) | Sprint 9. Critères : Session intégrée ; représentant légal ; preuve rattachée à l'historique du consentement Origine : N. |
| `VS-102` | En tant qu'administrateur du contrat, je veux basculer une organisation du CLM sur VinaSign | Must — pilote interne | À réaliser (story du backlog) | Sprint 10. Critères : Adaptateurs derrière les ports existants ; paramètre par organisation ; suite de caractérisation au vert pour les deux implémentations ; IQ exclu par défaut Origine : CLM. |
| `VS-103` | En tant qu'auditeur, je veux que les preuves CLM antérieures restent vérifiables | Must — pilote interne | À réaliser (story du backlog) | Sprint 10. Critères : Le certificat historique est reconnu ; vérification publique positive ; aucun document réémis Origine : CLM. |
| `VS-110` | En tant qu'équipe, je veux un modèle de menace revu | Must — pilote interne | À réaliser (story du backlog) | Sprint 8. Critères : STRIDE ([03](03-Securite-et-conformite.md)) relu par un pair ; chaque menace associée à un test Origine : N. |
| `VS-111` | En tant qu'équipe, je veux les tests d'intrusion automatisés sur tout l'inventaire | Must — pilote interne | À réaliser (story du backlog) | Sprint 9. Critères : `securite/` du contrat porté (INT-01 à INT-10), plus le rejeu de lien, d'assertion et de webhook ; ZAP et Trivy chaque nuit, bloquants Origine : CLM. |
| `VS-112` | En tant que responsable de la protection des renseignements, je veux l'EFVP et le registre | Must — pilote interne | À réaliser (story du backlog) | Sprint 9. Critères : EFVP signée ; résidence au Canada ; sous-traitants listés ; durées de conservation Origine : N. |
| `VS-113` | En tant qu'exploitant, je veux un PRA éprouvé | Must — pilote interne | À réaliser (story du backlog) | Sprint 10. Critères : Restauration à partir d'une sauvegarde et bascule vers Canada East mesurées (RPO 15 min, RTO 4 h) Origine : D1 § 7.3. |
| `VS-114` | En tant que direction, je veux un test d'intrusion indépendant avant le pilote | Must — pilote interne | À réaliser (story du backlog) | Sprint 10. Critères : Mandat attribué ; constats élevés corrigés ou faisant l'objet d'une décision de risque formelle Origine : D1 § 15.4. |
| `VS-115` | En tant que direction, je veux l'avis juridique sur les niveaux et le mode empreinte | Must — pilote interne | À réaliser (story du backlog) | Sprint 10. Critères : Avis écrit (LCCJTI, C.c.Q., renseignements de santé) ; terminologie de l'offre validée Origine : D1 § 10.4. |
| `VS-060` | En tant que préparateur, je veux placer, déplacer et redimensionner les champs au doigt | Should — V1 commerciale | À réaliser (story du backlog) | — [Un Word déposé au portail est affiché ; la préparatrice y place initiales et signature où elle veut, puis la signataire signe](#un-word-depose-au-portail-est-affiche-la-preparatrice-y-place-initiales-et-signature-ou-elle-veut-puis-la-signataire-signe) |
| `VS-061` | En tant que préparateur, je veux annuler et rétablir | Should — V1 commerciale | À réaliser (story du backlog) | — [Un Word déposé au portail est affiché ; la préparatrice y place initiales et signature où elle veut, puis la signataire signe](#un-word-depose-au-portail-est-affiche-la-preparatrice-y-place-initiales-et-signature-ou-elle-veut-puis-la-signataire-signe) |
| `VS-062` | En tant que préparateur, je veux des modèles d'enveloppe | Should — V1 commerciale | À réaliser (story du backlog) | Sprint 12. Critères : Champs et rôles réutilisables ; versionnés Origine : D1 US-103. |
| `VS-063` | En tant qu'intégrateur, je veux placer les champs par ancres textuelles | Should — V1 commerciale | À réaliser (story du backlog) | Sprint 12. Critères : `{{signature:1}}` dans le PDF ; position calculée ; erreur claire si l'ancre est absente Origine : N. |
| `VS-064` | En tant que préparateur, je veux envoyer depuis le portail, sans application cliente | Should — V1 commerciale | À réaliser (story du backlog) | — [Liste paginée des enveloppes, filtrée par statut](#liste-paginee-des-enveloppes-filtree-par-statut)<br>— [Préparée au portail sans placer de zone, l'enveloppe est signée à la main ; la signature figure sur le document scellé](#preparee-au-portail-sans-placer-de-zone-lenveloppe-est-signee-a-la-main-la-signature-figure-sur-le-document-scelle) |
| `VS-120` | En tant qu'organisation, je veux suivre ma consommation | Should — V1 commerciale | À réaliser (story du backlog) | — [L'organisation relève et exporte sa consommation](#lorganisation-releve-et-exporte-sa-consommation) |
| `VS-121` | En tant qu'organisation, je veux souscrire et payer un plan | Should — V1 commerciale | À réaliser (story du backlog) | — [L'écran de connexion du portail est accessible à toutes les largeurs](#lecran-de-connexion-du-portail-est-accessible-a-toutes-les-largeurs) |
| `VS-122` | En tant que prospect, je veux créer mon organisation en libre-service | Should — V1 commerciale | À réaliser (story du backlog) | — [Les étapes d'une inscription en libre-service exigent le jeton reçu par courriel](#les-etapes-dune-inscription-en-libre-service-exigent-le-jeton-recu-par-courriel)<br>— [Le formulaire d'inscription est accessible à toutes les largeurs](#le-formulaire-dinscription-est-accessible-a-toutes-les-largeurs) |
| `VS-123` | En tant qu'organisation, je veux personnaliser la marque | Should — V1 commerciale | À réaliser (story du backlog) | Sprint 13. Critères : Logo, couleurs, sous-domaine ; contraste vérifié par calcul Origine : D1 V1. |
| `VS-124` | En tant que client, je veux une page d'état et des SLO publiés | Should — V1 commerciale | À réaliser (story du backlog) | — [L'état du service se consulte sans authentification](#letat-du-service-se-consulte-sans-authentification)<br>— [La page d'état reste accessible à toutes les largeurs](#la-page-detat-reste-accessible-a-toutes-les-largeurs) |
| `VS-125` | En tant que développeur .NET, je veux un SDK | Should — V1 commerciale | À réaliser (story du backlog) | Sprint 15. Critères : .NET 8 ; tests de contrat Origine : D2 E15-11. |
| `VS-130` | En tant que signataire, je veux une signature avancée avec un certificat personnel | Could — V2 | À réaliser (story du backlog) | Critères : Certificat émis à la volée par l'autorité partenaire après vérification d'identité ; PAdES personnelle |
| `VS-131` | En tant qu'organisation, je veux exiger une vérification d'identité | Could — V2 | À réaliser (story du backlog) | — [Un rappel non signé par le fournisseur est refusé](#un-rappel-non-signe-par-le-fournisseur-est-refuse) |
| `VS-132` | En tant que système, je veux renouveler la validité à long terme | Could — V2 | À réaliser (story du backlog) | Critères : Horodatage d'archive avant l'expiration de la chaîne ; tâche planifiée ; alerte |
| `VS-133` | En tant que client entreprise, je veux apporter ma propre clé | Could — V2 | À réaliser (story du backlog) | Critères : Clé de données de l'organisation dans son Key Vault ; révocation = données illisibles |
| `VS-134` | En tant que client entreprise, je veux une instance dédiée | Could — V2 | À réaliser (story du backlog) | Critères : Même code ; déploiement isolé par Terraform ; procédure de mise à jour |
| `VS-135` | En tant que direction, je veux SOC 2 Type II puis ISO 27001 | Could — V2 | À réaliser (story du backlog) | Critères : Période d'observation ; audit externe |

## Exigences non éprouvées par un scénario

- `VS-001` — En tant qu'équipe, je veux le dépôt `vinasign` structuré par contexte (`domain/application/infrastructure`) afin de livrer des modules indépendants : **Documentaire.** Règles de dépendance et découpage vérifiés à chaque construction. Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/ArchitectureTest.java`.
- `VS-002` — En tant qu'équipe, je veux une CI qui compile, teste, mesure et analyse : **Documentaire.** Chaîne d'intégration continue : portes de couverture par classe et agrégée, domaine à 100 %, cliquet, mutation, Trivy, SBOM. Référence : `.github/workflows/ci.yml`.
- `VS-003` — En tant qu'exploitant, je veux des environnements reproductibles et des secrets externalisés : **Documentaire.** Infrastructure en code éprouvée sans compte infonuagique (terraform test, lint et rendu Helm). Reste : plan réel. Référence : `infra/verifier.sh`.
- `VS-004` — En tant qu'exploitant, je veux des journaux structurés, des métriques et des traces : **Documentaire.** Journal structuré et masqué, éprouvé sur l'application démarrée ; ArchUnit : seule l'infrastructure journalise. Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/commun/infrastructure/journal/JournalisationTest.java`.
- `VS-005` — En tant que client, je veux que la base isole les organisations : **Documentaire.** RLS éprouvée sur toutes les tables isolées, dont l'absence de toute ligne sans contexte. Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/commun/infrastructure/base/IsolationOrganisationTest.java`.
- `VS-015` — En tant qu'exploitant, je veux limiter le débit et appliquer des quotas par application : **Documentaire.** 429 avec Retry-After au-delà du plan, métriques par application : éprouvé sur la vraie pile (le serveur de la campagne BDD relève le débit par adresse). Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/organisation/DebitApiTest.java`.
- `VS-031` — En tant que système, je veux signer avec une clé du HSM (port `CleDeSignature`) : **Documentaire.** Contrat CleDeSignature exécuté contre PKCS#12 et Key Vault (émulateur Lowkey Vault, vrai SDK) ; clé exportable et PKCS#12 en production refusés. Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/signature/cle/`.
- `VS-033` — En tant que système, je veux horodater par RFC 3161 avec une autorité de repli : **Documentaire.** Bascule entre autorités d'horodatage et refus d'un jeton d'un autre document. Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/signature/infrastructure/pades/AutoritesHorodatageTest.java`.
- `VS-035` — En tant que système, je veux produire un PDF/A et convertir les formats bureautiques : **Documentaire.** Document Word converti par Gotenberg en PDF/A-2b jugé conforme par veraPDF (conteneurs réels) ; service sans accès réseau sortant (chart Helm). Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/enveloppe/infrastructure/controle/ControlesDocumentsTest.java`.
- `VS-081` — En tant que signataire, je veux recevoir mon code par SMS : **Documentaire.** Téléphone chiffré (AES-GCM), code par SMS via la boîte d'envoi et le simulateur ; simulateur refusé en production. Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/session/SessionApiTest.java`.
- `VS-082` — En tant qu'expéditeur, je veux des relances et une échéance : **Documentaire.** Relance après la fréquence, lien réémis (ancien révoqué), arrêt au maximum (plafond 10) ; aucun courriel pour une enveloppe annulée. Référence : `serveur/src/test/java/ca/vinaitsolution/vinasign/session/SessionApiTest.java`.

## Scénarios détaillés

### Accès — applications clientes

Une application cliente s'authentifie par une assertion signée de sa clé privée et reçoit un jeton de courte durée, limité à son organisation et à ses portées.

#### L'application obtient un jeton par client credentials et private_key_jwt

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Accès — applications clientes (`bdd/features/acces/01-applications-clientes.feature`)
- **Exigences éprouvées :** `VS-012`

```gherkin
Quand j'exécute le cas "acces.jeton"
Alors la plateforme se comporte comme attendu
```

**Cas `acces.jeton`** — L'application obtient un jeton de courte durée portant son organisation et ses portées ; sans jeton, l'API refuse *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/jeton` |  | 2xx | `$.portees` inclut "enveloppes-creer" ; `$.organisation` existe true |
| 2 | `GET /api/v1/jeton` | anonyme | 401 | — |

### Accès — organisations, utilisateurs du portail et applications

L'administration de la plateforme crée les organisations ; leurs administrateurs, authentifiés par le fournisseur d'identité avec MFA, gèrent leurs utilisateurs et leurs applications clientes.

#### Organisation, administrateur invité, rôles et MFA, suspension et réactivation

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Accès — organisations, utilisateurs du portail et applications (`bdd/features/acces/02-administration.feature`)
- **Exigences éprouvées :** `VS-010`, `VS-014`

```gherkin
Quand j'exécute le cas "organisations.cycle"
Alors la plateforme se comporte comme attendu
```

**Cas `organisations.cycle`** — La plateforme crée une organisation et invite son administrateur ; celui-ci n'a son rôle qu'avec une MFA ; suspension et réactivation coupent puis rendent l'accès ; le plan se choisit dans le référentiel *(données : `bdd/donnees/administration.json` ; profil : administrateur-plateforme)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/role-portail` |  | 2xx | capture `preparateur` ← `$.valeurs[?code=preparateur].code` |
| 2 | `GET /api/v1/referentiels/plan` |  | 2xx | capture `plan` ← `$.valeurs[1].code` |
| 3 | `POST /api/v1/organisations` |  | 201 | `$.statut` egal "active" ; `$.plan` existe true ; capture `org` ← `$.id` |
| 4 | `POST /api/v1/organisations/${org}/administrateurs` |  | 201 | — |
| 5 | `GET /api/v1/moi` |  | 2xx | `$.organisation` egal "${org}" ; `$.roles` inclut "administrateur-organisation" |
| 6 | `GET /api/v1/moi` |  | 2xx | `$.roles` exclut "administrateur-organisation" |
| 7 | `GET /api/v1/utilisateurs` |  | 403 | — |
| 8 | `POST /api/v1/utilisateurs` |  | 201 | capture `prep` ← `$.id` |
| 9 | `GET /api/v1/moi` |  | 2xx | `$.roles` inclut "preparateur" |
| 10 | `PUT /api/v1/utilisateurs/${prep}/roles` |  | 422 | `$.type` contient "role-non-attribuable" |
| 11 | `POST /api/v1/organisations/${org}/suspension` |  | 2xx | `$.statut` egal "suspendue" |
| 12 | `GET /api/v1/utilisateurs` |  | 403 | `$.type` contient "organisation-suspendue" |
| 13 | `POST /api/v1/organisations/${org}/reactivation` |  | 2xx | `$.statut` egal "active" |
| 14 | `GET /api/v1/utilisateurs` |  | 2xx | `$` longueurMin 2 |
| 15 | `PUT /api/v1/organisations/${org}/plan` |  | 2xx | `$.plan` egal "${plan}" |
| 16 | `POST /api/v1/utilisateurs/${prep}/desactivation` |  | 2xx | — |
| 17 | `GET /api/v1/moi` |  | 401 | — |

#### Application cliente enregistrée, rotation de ses clés et révocation immédiate

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Accès — organisations, utilisateurs du portail et applications (`bdd/features/acces/02-administration.feature`)
- **Exigences éprouvées :** `VS-011`, `VS-013`

```gherkin
Quand j'exécute le cas "applications.cycle"
Alors la plateforme se comporte comme attendu
```

**Cas `applications.cycle`** — L'administrateur enregistre une application (JWKS vérifiée, origine https) ; elle obtient un jeton ; rotation à deux clés puis retrait de l'ancienne ; une clé ne sert qu'à une application ; la révocation refuse aussitôt le jeton déjà émis *(données : `bdd/donnees/administration.json` ; profil : administrateur-plateforme)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/environnement-application` |  | 2xx | capture `env` ← `$.valeurs[0].code` |
| 2 | `GET /api/v1/referentiels/portee-api` |  | 2xx | capture `portee` ← `$.valeurs[0].code` |
| 3 | `POST /api/v1/organisations` |  | 201 | capture `org` ← `$.id` |
| 4 | `POST /api/v1/organisations/${org}/administrateurs` |  | 201 | — |
| 5 | `POST /api/v1/applications` |  | 400 | — |
| 6 | `POST /api/v1/applications` |  | 201 | ne contient pas « "n": » ; capture `client` ← `$.clientId` |
| 7 | `` |  | 2xx | — |
| 8 | `GET /api/v1/jeton` | anonyme | 2xx | `$.organisation` egal "${org}" |
| 9 | `PUT /api/v1/applications/${client}/cles` |  | 2xx | — |
| 10 | `` |  | 2xx | — |
| 11 | `` |  | 2xx | — |
| 12 | `PUT /api/v1/applications/${client}/cles` |  | 2xx | — |
| 13 | `` |  | 2xx | — |
| 14 | `POST /api/v1/applications` |  | 409 | `$.type` contient "cle-deja-enregistree" |
| 15 | `POST /api/v1/applications/${client}/revocation` |  | 2xx | `$.statut` egal "revoquee" |
| 16 | `GET /api/v1/jeton` | anonyme | 403 | `$.type` contient "application-revoquee" |
| 17 | `` |  | 2xx | — |

#### Les étapes d'une inscription en libre-service exigent le jeton reçu par courriel

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Accès — organisations, utilisateurs du portail et applications (`bdd/features/acces/02-administration.feature`)
- **Exigences éprouvées :** `VS-122`

```gherkin
Quand j'exécute le cas "organisation.inscription"
Alors la plateforme se comporte comme attendu
```

**Cas `organisation.inscription`** — Les étapes du demandeur sont authentifiées par le jeton reçu par courriel : un jeton inconnu ne révèle rien ; la validation appartient à l'administrateur de la plateforme *(données : `bdd/donnees/administration.json` ; profil : visiteur)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `POST /api/v1/signup/consultation` |  | 404 | `$.type` contient "inscription-introuvable" |
| 2 | `POST /api/v1/signup/domain-verification` |  | 404 | — |
| 3 | `GET /api/v1/platform/signups` |  | 401 | — |

#### Le formulaire d'inscription est accessible à toutes les largeurs

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Accès — organisations, utilisateurs du portail et applications (`bdd/features/acces/02-administration.feature`)
- **Exigences éprouvées :** `VS-122`

```gherkin
Quand j'ouvre l'écran "Portail — inscription"
Alors l'écran est accessible à 390, 768, 1280 et 1680 px de large
```

#### L'écran de connexion du portail est accessible à toutes les largeurs

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Accès — organisations, utilisateurs du portail et applications (`bdd/features/acces/02-administration.feature`)
- **Exigences éprouvées :** `VS-014`, `VS-121`

```gherkin
Quand j'ouvre l'écran "Portail — connexion"
Alors l'écran est accessible à 390, 768, 1280 et 1680 px de large
```

### Enveloppes et documents

Une application crée une enveloppe, désigne ses signataires dans un ordre, dépose le PDF par une URL présignée, puis l'envoie. Chaque transition est une action ; aucun statut ne se saisit.

#### Parcours complet d'une enveloppe en mode document

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Enveloppes et documents (`bdd/features/enveloppes/01-enveloppes.feature`)
- **Exigences éprouvées :** `VS-020`, `VS-021`, `VS-022`, `VS-023`, `VS-024`

```gherkin
Quand j'exécute le cas "enveloppes.parcours-document"
Alors la plateforme se comporte comme attendu
```

**Cas `enveloppes.parcours-document`** — Création idempotente, signataires ordonnés, dépôt par URL présignée à usage unique, contrôle et empreinte, envoi ; seul le premier ordre peut agir *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/langue` |  | 2xx | capture `langue` ← `$.valeurs[0].code` |
| 2 | `GET /api/v1/referentiels/mode-enveloppe` |  | 2xx | capture `mode` ← `$.valeurs[?code=document].code` |
| 3 | `GET /api/v1/referentiels/type-signataire` |  | 2xx | capture `invite` ← `$.valeurs[?code=invite].code` |
| 4 | `POST /api/v1/envelopes` |  | 201 | `$.statut` egal "brouillon" ; capture `id` ← `$.id` |
| 5 | `POST /api/v1/envelopes` |  | 201 | `$.id` egal "${id}" |
| 6 | `POST /api/v1/envelopes` |  | 422 | `$.type` contient "cle-idempotence-reutilisee" |
| 7 | `PUT /api/v1/envelopes/${id}/signers` |  | 2xx | `$.signataires` longueurMin 2 ; ne contient pas « @exemple.ca » |
| 8 | `POST /api/v1/envelopes/${id}/documents` |  | 201 | capture `url` ← `$.url` ; capture `depot` ← `$.id` |
| 9 | `PUT ${url}` | anonyme | 204 | — |
| 10 | `PUT ${url}` | anonyme | 422 | `$.type` contient "depot-invalide" |
| 11 | `POST /api/v1/envelopes/${id}/documents/${depot}/confirmation` |  | 2xx | `$.documents[0].nature` egal "original" ; `$.documents[0].pages` egal 1 |
| 12 | `POST /api/v1/envelopes/${id}/send` |  | 2xx | `$.statut` egal "envoyee" ; `$.signataires[0].peutAgir` egal true ; `$.signataires[1].peutAgir` egal false |
| 13 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.evenements[*].type` inclut "enveloppe-envoyee" |
| 14 | `PUT /api/v1/envelopes/${id}/signers` |  | 409 | — |

#### Refus explicites et annulation

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Enveloppes et documents (`bdd/features/enveloppes/01-enveloppes.feature`)
- **Exigences éprouvées :** `VS-021`, `VS-024`, `VS-025`

```gherkin
Quand j'exécute le cas "enveloppes.refus-et-annulation"
Alors la plateforme se comporte comme attendu
```

**Cas `enveloppes.refus-et-annulation`** — Un fichier qui n'est pas un PDF est refusé explicitement ; sans document, l'envoi est refusé ; l'annulation invalide le dépôt et ne se répète pas *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/mode-enveloppe` |  | 2xx | capture `mode` ← `$.valeurs[?code=document].code` |
| 2 | `POST /api/v1/envelopes` |  | 201 | capture `id` ← `$.id` |
| 3 | `POST /api/v1/envelopes/${id}/documents` |  | 201 | capture `url` ← `$.url` ; capture `depot` ← `$.id` |
| 4 | `PUT ${url}` | anonyme | 204 | — |
| 5 | `POST /api/v1/envelopes/${id}/documents/${depot}/confirmation` |  | 422 | `$.type` contient "document-type" |
| 6 | `POST /api/v1/envelopes/${id}/send` |  | 422 | `$.type` contient "signataires-requis" |
| 7 | `POST /api/v1/envelopes/${id}/documents` |  | 201 | capture `url2` ← `$.url` |
| 8 | `POST /api/v1/envelopes/${id}/cancel` |  | 2xx | `$.statut` egal "annulee" |
| 9 | `PUT ${url2}` | anonyme | 422 | — |
| 10 | `POST /api/v1/envelopes/${id}/cancel` |  | 409 | `$.type` contient "transition-enveloppe-interdite" |

#### Liste paginée des enveloppes, filtrée par statut

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Enveloppes et documents (`bdd/features/enveloppes/01-enveloppes.feature`)
- **Exigences éprouvées :** `VS-064`

```gherkin
Quand j'exécute le cas "enveloppes.liste"
Alors la plateforme se comporte comme attendu
```

**Cas `enveloppes.liste`** — L'application liste ses enveloppes, les plus récentes d'abord, page par page (curseur opaque), filtrées par un statut du référentiel ; un curseur falsifié est refusé *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/mode-enveloppe` |  | 2xx | capture `mode` ← `$.valeurs[?code=document].code` |
| 2 | `GET /api/v1/referentiels/statut-enveloppe` |  | 2xx | capture `brouillon` ← `$.valeurs[?code=brouillon].code` |
| 3 | `POST /api/v1/envelopes` |  | 201 | capture `premiere` ← `$.id` |
| 4 | `POST /api/v1/envelopes` |  | 201 | capture `seconde` ← `$.id` |
| 5 | `GET /api/v1/envelopes?limite=1&statut=${brouillon}` |  | 2xx | `$.elements[0].id` egal "${seconde}" ; `$.suivant` existe true ; capture `suivant` ← `$.suivant` |
| 6 | `GET /api/v1/envelopes?limite=1&statut=${brouillon}&apres=${suivant}` |  | 2xx | `$.elements[0].id` egal "${premiere}" |
| 7 | `GET /api/v1/envelopes?apres=falsifie` |  | 422 | — |

### Intégration — webhooks signés

L'application est avisée des événements de ses enveloppes par des webhooks signés (HMAC horodaté), repris pendant 72 heures en cas d'échec, et tracés. Elle ne peut déclarer qu'une destination HTTPS publique : jamais une adresse interne de la plateforme.

#### Déclarer, consulter et retirer une destination de webhooks, gardée contre la SSRF

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Intégration — webhooks signés (`bdd/features/integration/01-webhooks.feature`)
- **Exigences éprouvées :** `VS-091`

```gherkin
Quand j'exécute le cas "integration.webhooks"
Alors la plateforme se comporte comme attendu
```

**Cas `integration.webhooks`** — L'application déclare une destination de webhooks HTTPS : le secret de signature n'est remis qu'une fois ; une adresse interne ou un événement inconnu est refusé ; la destination se retire *(données : `bdd/donnees/administration.json` ; profil : application-webhooks)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `POST /api/v1/webhooks` |  | 201 | `$.secret` existe true ; capture `destination` ← `$.id` |
| 2 | `GET /api/v1/webhooks` |  | 2xx | `$[0].url` contient "recepteur-${unique}" ; ne contient pas « secret » |
| 3 | `POST /api/v1/webhooks` |  | 422 | `$.type` contient "destination-webhook-invalide" |
| 4 | `POST /api/v1/webhooks` |  | 422 | — |
| 5 | `POST /api/v1/webhooks` |  | 422 | — |
| 6 | `GET /api/v1/webhooks/${destination}/deliveries` |  | 2xx | ne contient pas « livreLe » |
| 7 | `DELETE /api/v1/webhooks/${destination}` |  | 204 | — |
| 8 | `DELETE /api/v1/webhooks/${destination}` |  | 404 | — |

#### L'organisation relève et exporte sa consommation

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Intégration — webhooks signés (`bdd/features/integration/01-webhooks.feature`)
- **Exigences éprouvées :** `VS-120`

```gherkin
Quand j'exécute le cas "integration.consommation"
Alors la plateforme se comporte comme attendu
```

**Cas `integration.consommation`** — L'organisation suit sa consommation : une enveloppe envoyée compte une fois, au mois de son premier envoi ; une enveloppe annulée avant envoi ne compte pas ; le relevé s'exporte en CSV *(données : `bdd/donnees/administration.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `POST /api/v1/envelopes` |  | 201 | capture `id` ← `$.id` |
| 2 | `PUT /api/v1/envelopes/${id}/signers` |  | 2xx | — |
| 3 | `POST /api/v1/envelopes/${id}/send` |  | 2xx | — |
| 4 | `GET /api/v1/usage?du=${aujourdhui}&au=${aujourdhui}` |  | 2xx | `$.envoyees` existe true ; `$.parMois` existe true |
| 5 | `GET /api/v1/usage.csv?du=${aujourdhui}&au=${aujourdhui}` |  | 2xx | `undefined` texteContient "mois,enveloppes" |
| 6 | `GET /api/v1/usage?du=${demain}&au=${aujourdhui}` |  | 422 | `$.type` contient "periode-invalide" |

#### L'intégrateur réinitialise son bac à sable

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Intégration — webhooks signés (`bdd/features/integration/01-webhooks.feature`)
- **Exigences éprouvées :** `VS-095`

```gherkin
Quand j'exécute le cas "acces.bac-a-sable"
Alors la plateforme se comporte comme attendu
```

**Cas `acces.bac-a-sable`** — Sur la plateforme de bac à sable, l'intégrateur repart d'une organisation vide ; rien n'est effacé *(données : `bdd/donnees/administration.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `POST /api/v1/sandbox/reset` |  | 2xx | `$.organisation` existe true |

### Signataire — invitation, lien sécurisé et consentement

L'envoi d'une enveloppe invite le signataire dont c'est le tour, par un courriel dans sa langue portant un lien personnel. Le lien ouvre une session courte ; le consentement à signer électroniquement est explicite et versionné.

#### Du courriel d'invitation au consentement et au code, puis l'annulation invalide le lien

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signataire — invitation, lien sécurisé et consentement (`bdd/features/signataire/01-invitation.feature`)
- **Exigences éprouvées :** `VS-040`, `VS-041`, `VS-042`, `VS-080`

```gherkin
Quand j'exécute le cas "signataire.invitation"
Alors la plateforme se comporte comme attendu
```

**Cas `signataire.invitation`** — L'envoi invite le premier signataire par courriel (lien personnel, sans pièce jointe) ; le lien ouvre une session ; le consentement versionné est retenu ; l'annulation invalide le lien *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/langue` |  | 2xx | capture `langue` ← `$.valeurs[?code=fr].code` |
| 2 | `GET /api/v1/referentiels/mode-enveloppe` |  | 2xx | capture `mode` ← `$.valeurs[?code=empreinte].code` |
| 3 | `GET /api/v1/referentiels/type-signataire` |  | 2xx | capture `invite` ← `$.valeurs[?code=invite].code` |
| 4 | `POST /api/v1/envelopes` |  | 201 | capture `id` ← `$.id` |
| 5 | `PUT /api/v1/envelopes/${id}/signers` |  | 2xx | — |
| 6 | `POST /api/v1/envelopes/${id}/send` |  | 2xx | — |
| 7 | `` |  | 2xx | — |
| 8 | `POST /api/v1/signataire/ouverture` | anonyme | 2xx | `$.nom` egal "Alice Tremblay" ; `$.peutAgir` egal true ; `$.aConsenti` egal false ; capture `session` ← `$.jetonSession` ; capture `version` ← `$.versionConsentement` |
| 9 | `POST /api/v1/signataire/consentement` | anonyme | 2xx | `$.empreinteTexte` existe true |
| 10 | `POST /api/v1/signataire/consentement` | anonyme | 401 | — |
| 11 | `POST /api/v1/signataire/ouverture` | anonyme | 2xx | `$.aConsenti` egal true |
| 12 | `GET /api/v1/referentiels/canal-code` |  | 2xx | capture `canal` ← `$.valeurs[?code=courriel].code` |
| 13 | `POST /api/v1/signataire/code` | anonyme | 202 | — |
| 14 | `` |  | 2xx | — |
| 15 | `POST /api/v1/signataire/code/verification` | anonyme | 2xx | `$.valide` egal true |
| 16 | `POST /api/v1/signataire/code/verification` | anonyme | 409 | `$.type` contient "code-deja-utilise" |
| 17 | `POST /api/v1/envelopes/${id}/cancel` |  | 2xx | — |
| 18 | `POST /api/v1/signataire/ouverture` | anonyme | 404 | `$.type` contient "lien-invalide" |

#### Session intégrée éphémère, à usage unique, pour l'utilisateur attesté par l'application

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signataire — invitation, lien sécurisé et consentement (`bdd/features/signataire/01-invitation.feature`)
- **Exigences éprouvées :** `VS-046`, `VS-044`

```gherkin
Quand j'exécute le cas "signataire.session-integree"
Alors la plateforme se comporte comme attendu
```

**Cas `signataire.session-integree`** — L'application atteste son utilisateur (assertion signée, VS-044) et obtient pour lui une session intégrée de 5 minutes, à usage unique, limitée aux actions demandées ; sans attestation valide, ou sans seconde preuve récente au niveau renforcé, elle est refusée *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/langue` |  | 2xx | capture `langue` ← `$.valeurs[?code=fr].code` |
| 2 | `GET /api/v1/referentiels/mode-enveloppe` |  | 2xx | capture `mode` ← `$.valeurs[?code=empreinte].code` |
| 3 | `GET /api/v1/referentiels/type-signataire` |  | 2xx | capture `utilisateur` ← `$.valeurs[?code=utilisateur-application].code` |
| 4 | `GET /api/v1/referentiels/action-signataire` |  | 2xx | capture `signer` ← `$.valeurs[?code=signer].code` |
| 5 | `POST /api/v1/envelopes` |  | 201 | capture `id` ← `$.id` |
| 6 | `PUT /api/v1/envelopes/${id}/signers` |  | 2xx | capture `signataire` ← `$.signataires[0].id` |
| 7 | `POST /api/v1/envelopes/${id}/send` |  | 2xx | — |
| 8 | `GET /api/v1/referentiels/niveau-authentification` |  | 2xx | capture `renforce` ← `$.valeurs[?code=renforce].code` |
| 9 | `POST /api/v1/envelopes/${id}/sessions` |  | 403 | `$.type` contient "attestation-refusee" |
| 10 | `` |  | 2xx | — |
| 11 | `POST /api/v1/envelopes/${id}/sessions` |  | 403 | `$.type` contient "attestation-refusee" |
| 12 | `` |  | 2xx | — |
| 13 | `POST /api/v1/envelopes/${id}/sessions` |  | 403 | `$.type` contient "attestation-refusee" |
| 14 | `` |  | 2xx | — |
| 15 | `POST /api/v1/envelopes/${id}/sessions` |  | 201 | capture `jeton` ← `$.url ~ /integre/([A-Za-z0-9_-]+)$` |
| 16 | `POST /api/v1/signataire/ouverture` | anonyme | 2xx | `$.actions` inclut "signer" ; `$.nom` egal "Dre Roy" ; `$.peutAgir` egal true |
| 17 | `POST /api/v1/signataire/ouverture` | anonyme | 404 | — |
| 18 | `POST /api/v1/envelopes/${id}/sessions` |  | 403 | `$.type` contient "attestation-refusee" |

### Signature d'empreinte et vérification publique

Une application fait signer l'empreinte d'un document de santé sans le transmettre ; n'importe quel tiers vérifie la signature, sans compte.

#### Empreinte signée et horodatée, puis vérifiée publiquement

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signature d'empreinte et vérification publique (`bdd/features/signataire/02-empreintes-et-verification.feature`)
- **Exigences éprouvées :** `VS-034`, `VS-036`

```gherkin
Quand j'exécute le cas "empreintes.signature-et-verification"
Alors la plateforme se comporte comme attendu
```

**Cas `empreintes.signature-et-verification`** — L'application fait signer l'empreinte de son document sans le transmettre ; un tiers vérifie publiquement la signature, horodatée ; une autre empreinte est refusée *(données : `bdd/donnees/enveloppes.json` ; profil : application-empreintes)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `POST /api/v1/empreintes/signature` |  | 2xx | `$.horodatee` egal true ; capture `cms` ← `$.cms` |
| 2 | `POST /api/v1/verifications` | anonyme | 2xx | `$.integre` egal true ; `$.niveau` egal "CAdES-B-T" |
| 3 | `POST /api/v1/verifications` | anonyme | 2xx | `$.integre` egal false |
| 4 | `POST /api/v1/verifications` | anonyme | 2xx | `$.integre` egal false ; `$.niveau` egal "" |

### Signataire — lire, remplir et signer à l'écran

Le signataire ouvre le lien reçu, consent à signer électroniquement, lit le document, remplit ses champs et signe en dessinant ou en tapant son nom. Chaque état de l'écran est accessible, sans débordement ni superposition, du téléphone au grand écran.

#### Du lien reçu par courriel à la signature au clavier, accessible à chaque étape

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signataire — lire, remplir et signer à l'écran (`bdd/features/signataire/03-parcours-ecran.feature`)
- **Exigences éprouvées :** `VS-040`, `VS-050`, `VS-051`, `VS-052`, `VS-053`, `VS-054`, `VS-070`, `VS-071`, `VS-072`, `VS-073`

```gherkin
Quand j'exécute le cas "signataire.parcours-ecran"
Et j'ouvre l'écran "Signataire — parcours (téléphone)"
Alors je vois "Consentement à la signature électronique"
Et l'écran est accessible, sans débordement ni superposition
Quand je coche "J'ai lu et j'accepte"
Et je clique sur "Continuer"
Alors je vois "Document à signer"
Et je vois "Page 1 / 1"
Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
Quand je clique sur "Signer le document"
Alors je vois "Des champs obligatoires sont vides"
Et l'écran est accessible, sans débordement ni superposition
Quand je saisis "Directrice" dans le champ "Titre du poste"
Et je clique sur "Taper mon nom"
Et je saisis "Camille Gagnon" dans le champ "Votre nom complet"
Et je clique sur "Signer le document"
Alors je vois "Merci, votre signature est enregistrée."
Et l'écran est accessible, sans débordement ni superposition
Quand j'exécute le cas "signataire.parcours-ecran-verification"
Alors la plateforme se comporte comme attendu
Quand j'ouvre l'écran "Signataire — parcours"
Alors je vois "Ce lien a déjà servi"
Et l'écran est accessible, sans débordement ni superposition
```

**Cas `signataire.parcours-ecran`** — Prépare une enveloppe « document » à un signataire, avec un champ texte obligatoire et une zone de signature, l'envoie et capture le lien reçu par courriel *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/langue` |  | 2xx | capture `langue` ← `$.valeurs[?code=fr].code` |
| 2 | `GET /api/v1/referentiels/mode-enveloppe` |  | 2xx | capture `mode` ← `$.valeurs[?code=document].code` |
| 3 | `GET /api/v1/referentiels/type-signataire` |  | 2xx | capture `invite` ← `$.valeurs[?code=invite].code` |
| 4 | `GET /api/v1/referentiels/type-champ` |  | 2xx | capture `texte` ← `$.valeurs[?code=texte].code` ; capture `zone` ← `$.valeurs[?code=signature].code` |
| 5 | `POST /api/v1/envelopes` |  | 201 | capture `id` ← `$.id` |
| 6 | `PUT /api/v1/envelopes/${id}/signers` |  | 2xx | capture `camille` ← `$.signataires[0].id` |
| 7 | `POST /api/v1/envelopes/${id}/documents` |  | 201 | capture `url` ← `$.url` ; capture `depot` ← `$.id` |
| 8 | `PUT ${url}` | anonyme | 204 | — |
| 9 | `POST /api/v1/envelopes/${id}/documents/${depot}/confirmation` |  | 2xx | — |
| 10 | `PUT /api/v1/envelopes/${id}/fields` |  | 2xx | — |
| 11 | `POST /api/v1/envelopes/${id}/send` |  | 2xx | — |
| 12 | `` |  | 2xx | — |

**Cas `signataire.parcours-ecran-verification`** — Après la signature à l'écran, la plateforme finalise l'enveloppe : document final scellé, journal intègre, certificat de réalisation et dossier de preuve *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.signataires[0].statut` egal "signe" ; `$.statut` egal "terminee" |
| 2 | `GET /api/v1/envelopes/${id}/documents/final` |  | 200 | — |
| 3 | `GET /api/v1/envelopes/${id}/preuve/integrite` |  | 2xx | `$.integre` egal true |
| 4 | `GET /api/v1/envelopes/${id}/preuve/certificat` |  | 200 | — |
| 5 | `GET /api/v1/envelopes/${id}/preuve/dossier` |  | 200 | — |
| 6 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.evenements[*].type` inclut "preuve-exportee" |

### Signataire — signature intégrée dans la page de l'application

L'application cliente affiche la signature dans sa propre page, sans renvoyer l'utilisateur ailleurs. Seules les origines qu'elle a déclarées peuvent l'afficher ; elle est avisée de l'issue, sans aucune donnée personnelle.

#### L'application intègre la session, l'utilisateur signe, l'application est avisée

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signataire — signature intégrée dans la page de l'application (`bdd/features/signataire/04-integration.feature`)
- **Exigences éprouvées :** `VS-055`, `VS-046`

```gherkin
Quand j'exécute le cas "signataire.integration-ecran"
Et l'application affiche la session intégrée dans sa page
Et le signataire consent et signe en tapant son nom dans la page de l'application
Alors l'application reçoit les avis "ready, signed"
```

**Cas `signataire.integration-ecran`** — Prépare une enveloppe « document » et obtient, pour son signataire, une session intégrée qui permet de consulter, signer et refuser *(données : `bdd/donnees/enveloppes.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/langue` |  | 2xx | capture `langue` ← `$.valeurs[?code=fr].code` |
| 2 | `GET /api/v1/referentiels/mode-enveloppe` |  | 2xx | capture `mode` ← `$.valeurs[?code=document].code` |
| 3 | `GET /api/v1/referentiels/type-signataire` |  | 2xx | capture `invite` ← `$.valeurs[?code=invite].code` |
| 4 | `GET /api/v1/referentiels/type-champ` |  | 2xx | capture `texte` ← `$.valeurs[?code=texte].code` ; capture `zone` ← `$.valeurs[?code=signature].code` |
| 5 | `POST /api/v1/envelopes` |  | 201 | capture `id` ← `$.id` |
| 6 | `PUT /api/v1/envelopes/${id}/signers` |  | 2xx | capture `camille` ← `$.signataires[0].id` |
| 7 | `POST /api/v1/envelopes/${id}/documents` |  | 201 | capture `url` ← `$.url` ; capture `depot` ← `$.id` |
| 8 | `PUT ${url}` | anonyme | 204 | — |
| 9 | `POST /api/v1/envelopes/${id}/documents/${depot}/confirmation` |  | 2xx | — |
| 10 | `PUT /api/v1/envelopes/${id}/fields` |  | 2xx | — |
| 11 | `POST /api/v1/envelopes/${id}/send` |  | 2xx | — |
| 12 | `GET /api/v1/referentiels/action-signataire` |  | 2xx | capture `consulter` ← `$.valeurs[?code=consulter].code` ; capture `signer` ← `$.valeurs[?code=signer].code` ; capture `refuser` ← `$.valeurs[?code=refuser].code` |
| 13 | `POST /api/v1/envelopes/${id}/sessions` |  | 201 | capture `integration` ← `$.url` |

### Signataire — vérification d'identité par un fournisseur (VS-131)

Une organisation exige une identité vérifiée : le signataire est envoyé chez le fournisseur choisi par configuration, dont le rappel signé élève la session. Le résultat (fournisseur, méthode, assurance) est consigné dans la preuve.

#### Un rappel non signé par le fournisseur est refusé

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signataire — vérification d'identité par un fournisseur (VS-131) (`bdd/features/signataire/05-verification-identite.feature`)
- **Exigences éprouvées :** `VS-131`

```gherkin
Quand j'exécute le cas "identite.rappel"
Alors la plateforme se comporte comme attendu
```

**Cas `identite.rappel`** — Le rappel du fournisseur d'identité est public mais n'est cru que signé du secret partagé ; le niveau « identité vérifiée » est un référentiel *(données : `bdd/donnees/socle.json` ; profil : visiteur)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/niveau-authentification?langue=fr` |  | 2xx | `$.valeurs[*].attributs.methode` inclut "verification-identite" |
| 2 | `POST /api/v1/verification-identite/rappel` |  | 403 | `$.type` contient "verification-identite-refusee" |

### Signataire — signer un document à la main, scellé et horodaté

Chaque signataire, à son tour, ouvre le lien reçu, consent, remplit ses champs (date, case à cocher) et signe en dessinant sa signature au doigt, au stylet ou à la souris ; ses initiales sont apposées par le même geste. Une fois toutes les signatures recueillies, la plateforme scelle le document final et le fait horodater par une autorité (RFC 3161). N'importe qui vérifie ce document sans compte, et la moindre altération est détectée.

#### Deux signatures manuscrites dans l'ordre, puis le document scellé et horodaté se vérifie publiquement

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signataire — signer un document à la main, scellé et horodaté (`bdd/features/signataire/06-signature-manuscrite.feature`)
- **Exigences éprouvées :** `VS-023`, `VS-040`, `VS-041`, `VS-051`, `VS-052`, `VS-033`, `VS-036`, `VS-070`, `VS-072`, `VS-073`

```gherkin
Quand j'exécute le cas "signature.manuscrite-preparation"
Alors la plateforme se comporte comme attendu
Quand j'ouvre l'écran "Signataire — signature manuscrite"
Et je coche "J'ai lu et j'accepte"
Et je clique sur "Continuer"
Alors je vois "Document à signer"
Quand je saisis "${aujourdhui}" dans le champ "Date de signature"
Et je coche "J'approuve les conditions"
Et je clique sur "Dessiner"
Et je clique sur "Signer le document"
Alors je vois "Dessinez votre signature ou tapez votre nom avant de signer."
Quand je dessine ma signature dans le "Cadre de signature"
Alors l'écran est accessible, sans débordement ni superposition
Quand je clique sur "Signer le document"
Alors je vois "Merci, votre signature est enregistrée."
Quand j'exécute le cas "signature.manuscrite-tour-suivant"
Alors la plateforme se comporte comme attendu
Quand j'ouvre l'écran "Signataire — signature manuscrite"
Et je coche "J'ai lu et j'accepte"
Et je clique sur "Continuer"
Et je clique sur "Dessiner"
Et je dessine ma signature dans le "Cadre de signature"
Et je clique sur "Signer le document"
Alors je vois "Merci, votre signature est enregistrée."
Quand j'exécute le cas "signature.manuscrite-verification"
Alors la plateforme se comporte comme attendu
```

**Cas `signature.manuscrite-preparation`** — Une enveloppe « document » à deux signataires dans l'ordre : Camille (date, case, initiales, signature) puis Jean (signature) ; seule Camille est invitée *(données : `bdd/donnees/signature.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/langue` |  | 2xx | capture `langue` ← `$.valeurs[?code=fr].code` |
| 2 | `GET /api/v1/referentiels/mode-enveloppe` |  | 2xx | capture `mode` ← `$.valeurs[?code=document].code` |
| 3 | `GET /api/v1/referentiels/type-signataire` |  | 2xx | capture `invite` ← `$.valeurs[?code=invite].code` |
| 4 | `GET /api/v1/referentiels/type-champ` |  | 2xx | capture `zone` ← `$.valeurs[?code=signature].code` ; capture `initiales` ← `$.valeurs[?code=initiales].code` ; capture `date` ← `$.valeurs[?code=date].code` ; capture `case` ← `$.valeurs[?code=case].code` |
| 5 | `POST /api/v1/envelopes` |  | 201 | capture `id` ← `$.id` |
| 6 | `PUT /api/v1/envelopes/${id}/signers` |  | 2xx | capture `camille` ← `$.signataires[0].id` ; capture `jean` ← `$.signataires[1].id` |
| 7 | `POST /api/v1/envelopes/${id}/documents` |  | 201 | capture `url` ← `$.url` ; capture `depot` ← `$.id` |
| 8 | `PUT ${url}` | anonyme | 204 | — |
| 9 | `POST /api/v1/envelopes/${id}/documents/${depot}/confirmation` |  | 2xx | — |
| 10 | `PUT /api/v1/envelopes/${id}/fields` |  | 2xx | — |
| 11 | `POST /api/v1/envelopes/${id}/send` |  | 2xx | — |
| 12 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.signataires[0].statut` different "signe" ; `$.signataires[1].statut` different "signe" ; `$.statut` different "terminee" |
| 13 | `` |  | 2xx | — |

**Cas `signature.manuscrite-tour-suivant`** — La signature de Camille est enregistrée, l'enveloppe n'est pas terminée ; Jean reçoit à son tour son invitation *(données : `bdd/donnees/signature.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.signataires[0].statut` egal "signe" ; `$.signataires[1].statut` different "signe" ; `$.statut` different "terminee" |
| 2 | `` |  | 2xx | — |

**Cas `signature.manuscrite-verification`** — Les deux signatures recueillies, la plateforme scelle le document final (PAdES, horodatage RFC 3161) ; n'importe qui le vérifie sans compte, et une altération d'un seul octet est détectée *(données : `bdd/donnees/signature.json` ; profil : application)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.signataires[0].statut` egal "signe" ; `$.signataires[1].statut` egal "signe" ; `$.statut` egal "terminee" |
| 2 | `GET /api/v1/envelopes/${id}/documents/final` |  | 200 | `undefined` texteContient "%PDF-" |
| 3 | `POST /api/v1/verifications` | anonyme | 2xx | `$.integre` egal true ; `$.signatures[0].valide` egal true ; `$.signatures[0].signataire` existe true ; `$.signatures[0].horodatage` existe true ; `$.niveau` egal "PAdES-B-LTA" ; `$.signatures[*].horodatageDeDocument` inclut true ; capture `niveau` ← `$.niveau` ; capture `horodatage` ← `$.signatures[0].horodatage` |
| 4 | `POST /api/v1/verifications` | anonyme | 200, 422 | ne contient pas « "integre":true » |
| 5 | `GET /api/v1/envelopes/${id}/preuve/integrite` |  | 2xx | `$.integre` egal true |
| 6 | `GET /api/v1/envelopes/${id}/preuve/certificat` |  | 200 | — |
| 7 | `GET /api/v1/envelopes/${id}/preuve/dossier` |  | 200 | — |

#### Préparée au portail sans placer de zone, l'enveloppe est signée à la main ; la signature figure sur le document scellé

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signataire — signer un document à la main, scellé et horodaté (`bdd/features/signataire/06-signature-manuscrite.feature`)
- **Exigences éprouvées :** `VS-064`, `VS-051`, `VS-070`, `VS-036`

```gherkin
Quand j'exécute le cas "portail.preparateur"
Alors la plateforme se comporte comme attendu
Quand je me connecte au portail en tant que "prep-sig-${unique}@exemple.ca"
Et je clique sur "Enveloppes"
Et je clique sur "Nouvelle enveloppe"
Et je saisis "PORTAIL-${UNIQUE}" dans le champ "Référence"
Et je choisis "Document" dans la liste "Mode de l'enveloppe"
Et je saisis "${dansUnMois}" dans le champ "Date limite de signature"
Et je joins un document PDF dans le champ "Document à signer"
Et je saisis "Léa Fortin" dans le champ "Nom complet"
Et je saisis "lea-${unique}@exemple.ca" dans le champ "Courriel"
Et je choisis "Invité" dans la liste "Type de signataire"
Alors l'écran est accessible, sans débordement ni superposition
Quand je clique sur "Envoyer pour signature"
Alors je vois "PORTAIL-${UNIQUE}"
Quand j'exécute le cas "portail.enveloppe-envoyee"
Alors la plateforme se comporte comme attendu
Quand j'ouvre l'écran "Signataire — signature manuscrite"
Et je coche "J'ai lu et j'accepte"
Et je clique sur "Continuer"
Alors je vois "Les zones de signature et d'initiales du document seront remplies par votre signature."
Quand je clique sur "Dessiner"
Et je dessine ma signature dans le "Cadre de signature"
Et je clique sur "Signer le document"
Alors je vois "Merci, votre signature est enregistrée."
Quand j'exécute le cas "portail.enveloppe-verification"
Alors la plateforme se comporte comme attendu
```

**Cas `portail.preparateur`** — La plateforme crée une organisation et invite son administrateur ; celui-ci invite une personne qui prépare et suit les enveloppes au portail (préparatrice et auditrice) *(données : `bdd/donnees/signature.json` ; profil : administrateur-plateforme)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/role-portail` |  | 2xx | capture `preparateur` ← `$.valeurs[?code=preparateur].code` ; capture `auditeur` ← `$.valeurs[?code=auditeur].code` |
| 2 | `POST /api/v1/organisations` |  | 201 | capture `org` ← `$.id` |
| 3 | `POST /api/v1/organisations/${org}/administrateurs` |  | 201 | — |
| 4 | `POST /api/v1/utilisateurs` |  | 201 | — |

**Cas `portail.enveloppe-envoyee`** — L'enveloppe préparée à l'écran est envoyée ; sans zone placée, chaque signataire en reçoit une à l'envoi ; la signataire reçoit son invitation *(données : `bdd/donnees/signature.json` ; profil : administrateur-plateforme)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/envelopes?limite=1` |  | 2xx | `$.elements[0].referenceExterne` egal "PORTAIL-${UNIQUE}" ; `$.elements[0].statut` egal "envoyee" ; capture `id` ← `$.elements[0].id` |
| 2 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.evenements[*].type` inclut "zones-signature-par-defaut" |
| 3 | `` |  | 2xx | — |

**Cas `portail.enveloppe-verification`** — La signature manuscrite recueillie, le document final est scellé et horodaté ; la préparatrice le télécharge et n'importe qui le vérifie *(données : `bdd/donnees/signature.json` ; profil : administrateur-plateforme)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.statut` egal "terminee" |
| 2 | `GET /api/v1/envelopes/${id}/documents/final` |  | 2xx | — |
| 3 | `POST /api/v1/verifications` | anonyme | 2xx | `$.integre` egal true ; `$.niveau` egal "PAdES-B-LTA" ; `$.signatures[0].horodatage` existe true |

#### Un Word déposé au portail est affiché ; la préparatrice y place initiales et signature où elle veut, puis la signataire signe

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Signataire — signer un document à la main, scellé et horodaté (`bdd/features/signataire/06-signature-manuscrite.feature`)
- **Exigences éprouvées :** `VS-035`, `VS-060`, `VS-061`, `VS-051`, `VS-070`, `VS-036`

```gherkin
Quand j'exécute le cas "portail.preparateur"
Alors la plateforme se comporte comme attendu
Quand je me connecte au portail en tant que "prep-sig-${unique}@exemple.ca"
Et je clique sur "Enveloppes"
Et je clique sur "Nouvelle enveloppe"
Et je saisis "WORD-${UNIQUE}" dans le champ "Référence"
Et je saisis "${dansUnMois}" dans le champ "Date limite de signature"
Et je joins le document Word "contrat.docx" dans le champ "Document à signer"
Et je saisis "Léa Fortin" dans le champ "Nom complet"
Et je saisis "lea-${unique}@exemple.ca" dans le champ "Courriel"
Et je clique sur "Placer les signatures sur le document"
Alors je vois "Document à signer"
Et je vois "Page 1 / 1"
Quand je choisis "Initiales" dans la liste "Type de zone"
Et je clique sur "Ajouter une zone à la page 1"
Et je clique sur "Déplacer vers le haut"
Et je choisis "Signature" dans la liste "Type de zone"
Et je clique sur "Ajouter une zone à la page 1"
Et je clique sur "Déplacer vers le bas"
Alors je vois "2 zone(s) placée(s)"
Et l'écran est accessible à 390, 768, 1280 et 1680 px de large
Quand je clique sur "Annuler"
Alors je vois "Initiales — Léa Fortin"
Quand je clique sur "Rétablir"
Et je clique sur "Envoyer pour signature"
Alors je vois "WORD-${UNIQUE}"
Quand j'exécute le cas "portail.enveloppe-placee"
Alors la plateforme se comporte comme attendu
Quand j'ouvre l'écran "Signataire — signature manuscrite"
Et je coche "J'ai lu et j'accepte"
Et je clique sur "Continuer"
Et je clique sur "Dessiner"
Et je dessine ma signature dans le "Cadre de signature"
Et je clique sur "Signer le document"
Alors je vois "Merci, votre signature est enregistrée."
Quand j'exécute le cas "portail.enveloppe-verification"
Alors la plateforme se comporte comme attendu
```

**Cas `portail.preparateur`** — La plateforme crée une organisation et invite son administrateur ; celui-ci invite une personne qui prépare et suit les enveloppes au portail (préparatrice et auditrice) *(données : `bdd/donnees/signature.json` ; profil : administrateur-plateforme)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/role-portail` |  | 2xx | capture `preparateur` ← `$.valeurs[?code=preparateur].code` ; capture `auditeur` ← `$.valeurs[?code=auditeur].code` |
| 2 | `POST /api/v1/organisations` |  | 201 | capture `org` ← `$.id` |
| 3 | `POST /api/v1/organisations/${org}/administrateurs` |  | 201 | — |
| 4 | `POST /api/v1/utilisateurs` |  | 201 | — |

**Cas `portail.enveloppe-placee`** — Les zones placées à l'écran sont celles de l'enveloppe : initiales et signature de la signataire, à la page choisie ; aucune zone par défaut n'est ajoutée ; le Word a été converti en PDF *(données : `bdd/donnees/signature.json` ; profil : administrateur-plateforme)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/envelopes?limite=1` |  | 2xx | `$.elements[0].referenceExterne` egal "WORD-${UNIQUE}" ; `$.elements[0].statut` egal "envoyee" ; capture `id` ← `$.elements[0].id` |
| 2 | `GET /api/v1/envelopes/${id}/fields` |  | 2xx | `$[*].type` inclut "initiales" ; `$[*].type` inclut "signature" ; `$[0].page` egal 1 |
| 3 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.evenements[*].type` exclut "zones-signature-par-defaut" ; `$.documents[0].pages` egal 1 |
| 4 | `GET /api/v1/envelopes/${id}/documents/original` |  | 2xx | `undefined` texteContient "%PDF-" |
| 5 | `` |  | 2xx | — |

**Cas `portail.enveloppe-verification`** — La signature manuscrite recueillie, le document final est scellé et horodaté ; la préparatrice le télécharge et n'importe qui le vérifie *(données : `bdd/donnees/signature.json` ; profil : administrateur-plateforme)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/envelopes/${id}` |  | 2xx | `$.statut` egal "terminee" |
| 2 | `GET /api/v1/envelopes/${id}/documents/final` |  | 2xx | — |
| 3 | `POST /api/v1/verifications` | anonyme | 2xx | `$.integre` egal true ; `$.niveau` egal "PAdES-B-LTA" ; `$.signatures[0].horodatage` existe true |

### Socle — listes dynamiques (règle « tout est dynamique »)

Toute liste de la plateforme vient d'un référentiel administrable, servi par l'API et traduit. Aucune option n'est écrite dans le code : une valeur ajoutée en base apparaît sans redéploiement.

#### Chaque référentiel annoncé par l'API se consulte dans la langue demandée

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Socle — listes dynamiques (règle « tout est dynamique ») (`bdd/features/socle/01-listes-dynamiques.feature`)
- **Exigences éprouvées :** `VS-R01`

```gherkin
Quand j'exécute le cas "socle.referentiels"
Alors la plateforme se comporte comme attendu
```

**Cas `socle.referentiels`** — Les référentiels sont découverts par l'API (aucun code connu à l'avance) ; chacun se lit dans la langue demandée ; un code inconnu rend un problème RFC 9457 *(données : `bdd/donnees/socle.json` ; profil : visiteur)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels?langue=fr` |  | 2xx | `$` longueurMin 1 ; capture `premier` ← `$[0].code` |
| 2 | `GET /api/v1/referentiels/${premier}?langue=en` |  | 2xx | `$.langue` egal "en" ; `$.valeurs` longueurMin 1 |
| 3 | `GET /api/v1/referentiels/inexistant-${unique}` |  | 404 | `$.type` contient "referentiel-inconnu" |
| 4 | `GET /api/v1/referentiels/MAJUSCULES` |  | 400 | `$.type` contient "requete-invalide" |

#### Le portail tire son choix de langue du référentiel et change de langue sans rechargement

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Socle — listes dynamiques (règle « tout est dynamique ») (`bdd/features/socle/01-listes-dynamiques.feature`)
- **Exigences éprouvées :** `VS-R01`

```gherkin
Quand j'ouvre l'écran "Portail — référentiels"
Alors l'écran est accessible, sans débordement ni superposition
Quand je choisis "Anglais" dans la liste "Langue"
Alors je vois "Reference lists"
Et l'écran est accessible, sans débordement ni superposition
```

#### Le portail et l'écran du signataire restent accessibles à toutes les largeurs

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Socle — listes dynamiques (règle « tout est dynamique ») (`bdd/features/socle/01-listes-dynamiques.feature`)
- **Exigences éprouvées :** `VS-R01`

```gherkin
Quand j'ouvre l'écran "Portail — référentiels (téléphone)"
Alors l'écran est accessible, sans débordement ni superposition
Quand j'ouvre l'écran "Portail — référentiels (tablette)"
Alors l'écran est accessible, sans débordement ni superposition
Quand j'ouvre l'écran "Portail — référentiels (grand écran)"
Alors l'écran est accessible, sans débordement ni superposition
Quand j'ouvre l'écran "Signataire — accueil (téléphone)"
Alors l'écran est accessible, sans débordement ni superposition
```

### Socle — page d'état et objectifs de service (VS-124)

L'état du service, l'historique des incidents et les objectifs publiés sont consultables sans compte. Les composants, les impacts, les phases et les objectifs sont des référentiels : rien n'est écrit dans l'interface.

#### L'état du service se consulte sans authentification

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Socle — page d'état et objectifs de service (VS-124) (`bdd/features/socle/02-etat-du-service.feature`)
- **Exigences éprouvées :** `VS-124`

```gherkin
Quand j'exécute le cas "etat.public"
Alors la plateforme se comporte comme attendu
```

**Cas `etat.public`** — La page d'état est publique : impact global, impact de chaque composant du référentiel, historique ; les objectifs de service sont un référentiel *(données : `bdd/donnees/socle.json` ; profil : visiteur)*

| # | Appel | Profil | Statut attendu | Vérifications et captures |
|---|---|---|---|---|
| 1 | `GET /api/v1/referentiels/impact-incident?langue=fr` |  | 2xx | `$.valeurs` longueurMin 2 ; capture `normal` ← `$.valeurs[0].code` |
| 2 | `GET /api/v1/referentiels/objectif-service?langue=fr` |  | 2xx | `$.valeurs` longueurMin 1 |
| 3 | `GET /api/v1/availability` |  | 2xx | `$.impact` longueurMin 1 ; `$.incidents` existe true |
| 4 | `POST /api/v1/platform/incidents` |  | 401 | — |

#### La page d'état reste accessible à toutes les largeurs

- **Résultat de la dernière exécution :** — non exécuté
- **Fonctionnalité :** Socle — page d'état et objectifs de service (VS-124) (`bdd/features/socle/02-etat-du-service.feature`)
- **Exigences éprouvées :** `VS-124`

```gherkin
Quand j'ouvre l'écran "Portail — état du service"
Alors l'écran est accessible à 390, 768, 1280 et 1680 px de large
```

