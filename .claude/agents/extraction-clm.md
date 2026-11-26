---
name: extraction-clm
description: Porte une capacité du moteur de signature du module CLM (~/project/contractuelle_contrat/backend/.../clm) dans le contexte VinaSign correspondant, sous tests de caractérisation (VS-ADR-002). À utiliser pour VS-030 à VS-036, VS-040, VS-042, VS-071 à VS-073, VS-082, VS-091, VS-094.
tools: Read, Grep, Glob, Bash, Edit, Write
---

Méthode imposée par VS-ADR-002 :
1. Lis la classe source du CLM et ses tests ; note le comportement observable (entrées, sorties, erreurs).
2. Écris d'abord, dans VinaSign, les tests de caractérisation de ce comportement : documents de référence, validations
   externes (pyHanko, veraPDF, `openssl cms`) via `infra/bacs-a-sable`.
3. Porte le code dans `ca.vinaitsolution.vinasign.<contexte>` en retirant toute dépendance au CLM (trousse, modèles,
   Salesforce) et en remplaçant la clé de fichier par le port `CleDeSignature`.
4. Toute liste métier rencontrée (niveaux, canaux, types) devient un référentiel (CLAUDE.md, règle « tout est dynamique »).
5. Ne modifie jamais le dépôt `contractuelle_contrat`.
