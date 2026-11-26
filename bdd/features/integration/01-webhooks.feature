# language: fr
@VINASIGN
Fonctionnalité: Intégration — webhooks signés
  L'application est avisée des événements de ses enveloppes par des webhooks signés (HMAC horodaté), repris pendant
  72 heures en cas d'échec, et tracés. Elle ne peut déclarer qu'une destination HTTPS publique : jamais une adresse
  interne de la plateforme.

  @VS-091
  Scénario: Déclarer, consulter et retirer une destination de webhooks, gardée contre la SSRF
    Quand j'exécute le cas "integration.webhooks"
    Alors la plateforme se comporte comme attendu

  @VS-120
  Scénario: L'organisation relève et exporte sa consommation
    Quand j'exécute le cas "integration.consommation"
    Alors la plateforme se comporte comme attendu

  @VS-095
  Scénario: L'intégrateur réinitialise son bac à sable
    Quand j'exécute le cas "acces.bac-a-sable"
    Alors la plateforme se comporte comme attendu
