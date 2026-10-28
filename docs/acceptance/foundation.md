# Recette — Socle RH v0.1
Date : 28 octobre 2026. Environnement : local, données de démonstration.

## Résultats
| Vérification | Résultat |
|---|---|
| Spring Boot : accès métier, isolation par établissement, PostgreSQL/Flyway, conversion de rôles, JWT signé, PKCE et session | 27 tests réussis, aucun ignoré |
| Angular : connexion, données serveur, erreurs, session expirée, accès refusé, langues, navigation, réessai et impression | 11 tests réussis |
| Chrome : accès anonyme refusé, owner cinq sites, caissier un site, stock dépôt, spoofing refusé, CSRF, logout et A4 arabe | 4 parcours réussis |
| Build Maven Wrapper 3.9.14 | BUILD SUCCESS via tools/verify-backend.ps1 |
| Build Angular production | Réussi, bundle initial environ 170 kB bruts |
| Docker Compose | PostgreSQL, Keycloak, Administration, Gateway et Web healthy |
| Revue Java, TypeScript et revue globale | Corrections intégrées ; approbation finale du socle local |

## Couverture mesurée
- Administration : 31/35 lignes, soit 88,57 %.
- Gateway : 41/47 lignes, soit 87,23 %.
- Angular : 100 % des lignes, instructions et fonctions mesurées ; 94,73 % des branches.
Ces chiffres concernent le code du jalon, pas les modules métier futurs.

## Parcours réel
Les tests navigateur utilisent des comptes réellement authentifiés dans Keycloak et des données PostgreSQL.
Les tests JWT d'intégration emploient des signatures RSA vérifiées et refusent audience, issuer ou signature incorrects.
Les tests de rôle dédiés traversent le convertisseur réel des claims realm_access.
Le formulaire logout traverse le reverse proxy et Keycloak ; la session est ensuite vérifiée non authentifiée.
La fiche A4 arabe a été rendue et inspectée visuellement : une page avec marges de 16 mm.

## Corrections issues des tests
- Sessions expirées et accès refusés : récupération et logout restent possibles.
- PKCE S256 explicitement activé pour le client confidentiel.
- Le CSP autorise l'origine locale Keycloak pour le redirect de logout.
- Les healthchecks utilisent une adresse IPv4 explicite lorsque nécessaire.
- Port public 4300, après refus de bind Windows sur 8080.
- Répertoire de sockets Java configurable localement pour contourner un problème AF_UNIX Windows.

## Périmètre livré
Gateway et Administration sont les deux services applicatifs opérationnels.
Produits, stock commercial, achats, ventes, comptes financiers, SAV et service PDF consolidé restent à construire.
L'impression livrée est la fiche navigateur des établissements, pas la facturation.
Le workflow GitHub Actions est fourni mais n'a pas été exécuté sur GitHub ; aucun dépôt distant n'a été créé.
La configuration livrée est destinée au développement local.
