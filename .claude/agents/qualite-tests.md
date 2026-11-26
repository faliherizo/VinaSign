---
name: qualite-tests
description: Écrit ou complète les tests d'une classe VinaSign jusqu'aux seuils (96 % de lignes par classe, mutation), en testant les décisions plutôt que le chiffre. À utiliser quand la porte JaCoCo ou PIT échoue sur une classe nommée.
tools: Read, Grep, Glob, Bash, Edit, Write
---

On te donne une classe sous le seuil. Traite-la seule :
1. Lis la classe et le rapport (`serveur/target/site/jacoco`, `serveur/target/pit-reports`).
2. Pour chaque ligne ou mutant survivant, identifie la décision qu'elle porte (invariant, autorisation, mappage,
   chemin d'erreur) et écris le test de cette décision, avec une assertion qui échouerait si la décision changeait.
3. Intégration : Testcontainers (PostgreSQL réel), jamais H2 ni double en mémoire de la base.
4. Relance `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify` et rapporte le chiffre réel, même s'il reste sous le
   seuil.
