# Point de reprise RH
28 octobre 2026.

## Jalon validé — socle local
Socle exécutable : Gateway Spring Cloud + Administration Spring Boot + Angular, PostgreSQL et Keycloak.
Les services Inventory, Commerce, Finance et Insights ne sont pas encore implémentés.

## Décisions actées
- Java 21 ; Spring Boot 3.5.16 ; Spring Cloud 2025.0.3.
- Angular 21, compatible avec Node 24.14 installé.
- Compose local, ports seulement sur loopback.
- Données d'exemple isolées du profil par défaut.
- Les tokens OIDC restent dans le BFF ; Angular n'utilise pas localStorage pour l'authentification.
- La préférence de langue seule peut être mémorisée dans localStorage.
- Pas d'inscription publique, pas de mot de passe de démonstration fixe.
- Requêtes d'établissements en lecture seule dans BranchQueryService.
- Le budget CSS du composant racine est de 10/12 kB pour le layout trilingue ; budget initial JS conservé.

## Critères avant fin du jalon
Tests Maven complets, tests Angular, build, quatre parcours navigateur OIDC, contrôle de la sortie A4 arabe, revue du code et archive source sans secrets.
Résultats obtenus : 27 tests Spring, 11 tests Angular et 4 parcours Chrome réussis. Voir docs/acceptance/foundation.md.

## Reprise
1. Lire README.md, ce fichier et la conception V2.
2. Vérifier docker compose ps et les tests.
3. Ne pas supprimer .env, le realm importé ou les volumes pour corriger un problème de code.
4. Respecter les frontières des services avant de créer les prochains modules.
5. Le rôle owner n'est pas déduit du prénom ; les affectations utilisent le subject OIDC.

## Questions métier toujours ouvertes
Entité(s) juridique(s), rôle exact du second Rachid, vendeurs par site, IMEI obligatoire, seuils d'approbation, garanties et régime fiscal.
Ces points ne bloquent pas les fixtures locales ; ils bloquent les fonctions métier réelles qui en dépendent.
