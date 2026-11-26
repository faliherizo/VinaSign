# SLI, SLO et SLA — VinaSign

Les cibles sont celles du dossier d'architecture ([02, § 8](../docs/architecture/02-Dossier-d-architecture.md)). Les plans
JMeter **valident** la latence et le taux d'erreur par `run.sh`, qui sort en erreur au-delà. La disponibilité se mesure
en continu par la supervision.

| SLO | Cible | Variable de `run.sh` |
|---|---|---|
| Latence en lecture, P95 (hors traitement PDF) | ≤ 300 ms | `SLO_READ_P95` |
| Latence en lecture, P99 | ≤ 600 ms | `SLO_READ_P99` |
| Latence en écriture, P95 | ≤ 1 500 ms | `SLO_WRITE_P95` |
| Taux d'erreur | < 0,1 % | `SLO_ERROR_PCT` |
| Signature d'empreinte de bout en bout, P95 (horodatage compris) | ≤ 1,5 s | plan à ajouter avec VS-034 |
| Disponibilité | 99,9 % au pilote ; 99,95 % visé en SaaS | supervision |

## Plans

| Plan | Ce qu'il charge |
|---|---|
| `vinasign-referentiels.jmx` | Découverte des référentiels, puis lecture de chacun |

Chaque plan découvre ses données par l'API : un plan ne cite ni identifiant ni code.

```bash
./run.sh                                   # 20 utilisateurs, montée de 30 s, 5 boucles
THREADS=50 LOOPS=10 ./run.sh               # charge accrue
PLAN=autre-plan.jmx ./run.sh
```
