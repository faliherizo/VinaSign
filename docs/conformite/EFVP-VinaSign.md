# Évaluation des facteurs relatifs à la vie privée (EFVP) — VinaSign

- **Statut** : projet, à valider et à signer par la personne responsable de la protection des renseignements personnels (VS-112)
- **Date** : 2026-11-22
- **Cadre** : Loi sur la protection des renseignements personnels dans le secteur privé (Québec, loi 25) ; pour les
  organisations publiques clientes, Loi sur l'accès (art. 63.5) ; Loi concernant le cadre juridique des technologies
  de l'information (LCCJTI)

## 1. Objet

VinaSign recueille le consentement à signer électroniquement, puis la signature, pour des documents qu'une
**organisation cliente** lui confie. Pour ces renseignements, VinaSign agit comme **mandataire** de l'organisation, qui
reste responsable du traitement. VinaSign ne décide ni des finalités ni des personnes invitées à signer.

## 2. Renseignements traités

| Renseignement | Qui | Source | Finalité | Protection | Conservation (proposée) |
|---|---|---|---|---|---|
| Nom du signataire | Signataire | Organisation cliente (API) | Désigner la personne, l'afficher, l'inscrire au certificat | RLS par organisation ; jamais journalisé (masquage) | Durée de vie de l'enveloppe et de sa preuve |
| Courriel | Signataire | Organisation cliente | Invitation, code, confirmation | RLS ; corps des courriels effacé après l'envoi ; absent des webhooks et du MCP | Idem |
| Téléphone | Signataire | Organisation cliente | Code par SMS | Chiffré champ par champ (AES-256-GCM, clé du coffre) ; texte du SMS effacé après l'envoi | Idem |
| Sujet externe | Utilisateur de l'application | Application cliente | Lier l'attestation à la bonne personne (VS-044) | RLS ; empreinte seulement pour les clés d'accès | Idem |
| Image de la signature ou nom tapé | Signataire | Signataire | Apposer la signature | Stockage objet immuable par organisation ; aucune donnée biométrique de tracé (vitesse, pression) | Idem |
| Valeurs des champs | Signataire | Signataire | Compléter le document | RLS | Idem |
| Consentement (version, empreinte du texte, heure) | Signataire | Signataire | Preuve du consentement | Journal de preuve en ajout seul | Idem |
| Niveau d'authentification, méthodes (`amr`) | Signataire | Code, clé d'accès, attestation | Preuve du niveau atteint | Codes : empreinte HMAC seulement ; clés d'accès : clé publique seulement | Idem |
| Document à signer | Organisation, signataire | Organisation cliente | Signature | Antivirus, stockage privé immuable ; mode empreinte : le document n'est jamais transmis (données de santé) | Selon l'organisation |
| Courriel et rôles des utilisateurs du portail | Personnel de l'organisation | Fournisseur d'identité | Administration | RLS ; MFA exigée pour les rôles administratifs | Tant que le compte est actif |

Aucune adresse IP ni empreinte d'appareil n'est conservée à des fins de profilage. Les journaux techniques ne
contiennent aucun renseignement personnel (`MasqueRenseignements`, VS-004).

## 3. Résidence et sous-traitants

| Sous-traitant | Service | Lieu des données |
|---|---|---|
| Microsoft Azure | Hébergement (AKS, PostgreSQL, stockage, Key Vault, Front Door) | Canada Central, reprise : Canada East (variables Terraform validées : régions canadiennes seulement) |
| Fournisseur SMS | Envoi des codes | **À choisir en phase 0** (exiger l'hébergement au Canada) |
| Relais de courriel | Envoi des invitations | **À choisir** (idem) |
| Autorité de certification partenaire | Certificats et horodatage | **À choisir** ; ne reçoit que des empreintes |

## 4. Risques et mesures

| Risque | Mesure | Preuve |
|---|---|---|
| Accès d'une organisation aux données d'une autre | RLS sur toutes les tables, rôle applicatif non propriétaire | `IsolationOrganisationTest`, INT-03 |
| Lien d'invitation intercepté | Jeton de 256 bits haché, usage borné, expiration ; code ou clé d'accès selon le niveau | `SessionApiTest`, `CleAccesApiTest` |
| Divulgation par les intégrations | Webhooks et MCP sans renseignement personnel | `WebhookApiTest`, `McpApiTest` |
| Fuite par les journaux | Masquage des renseignements, aucun corps de requête journalisé | `FormatJournalTest` |
| Données de santé | Mode empreinte : seul le condensé SHA-256 quitte l'établissement | `EmpreinteApiTest` |
| Conservation excessive | **Écart** : aucune purge automatique n'existe encore | Voir § 5 |

## 5. Points à trancher avant le pilote

1. **Durées de conservation** par catégorie, par organisation : elles doivent être paramétrables (référentiel), avec
   une purge qui préserve la preuve. Le journal de preuve est en ajout seul : définir ce qui en est retiré, et comment.
2. Choix et contrats des sous-traitants SMS et courriel, avec hébergement au Canada.
3. Modèle d'entente de mandataire avec chaque organisation cliente.
4. Procédure d'incident de confidentialité (registre, avis à la Commission d'accès à l'information).

## 6. Registre des traitements (extrait)

| Traitement | Base | Personnes concernées | Responsable |
|---|---|---|---|
| Signature électronique de documents | Mandat de l'organisation cliente ; consentement du signataire à la signature électronique | Signataires | Organisation cliente |
| Administration du portail | Contrat de service | Personnel des organisations | Vina IT Solution |
