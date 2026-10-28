# RH Gestion
Application interne RH — Spring Boot microservices et Angular.

## Socle validé
- Connexion OIDC Keycloak avec Authorization Code + PKCE S256.
- BFF : les jetons restent côté serveur ; session HttpOnly, protection CSRF et logout OIDC.
- Administration Spring Boot et PostgreSQL/Flyway.
- Liste des établissements filtrée côté serveur par rôle et affectation.
- Dépôt + quatre magasins dans les données locales de démonstration.
- Interface français, arabe RTL et anglais.
- Impression A4 de la fiche des établissements.
- Tests unitaires, API, PostgreSQL réel/JWT signé et parcours navigateur.

Le module catalogue et stock est maintenant implémenté : variantes couleur/RAM/stockage, stock par site, ouvertures, ajustements et transferts avec réception partielle. Sa validation navigateur complète reste à terminer ; voir docs/STATUS.md. Ventes, crédits, caisse et SAV restent à développer.

## Prérequis
Windows : PowerShell 7, Java 21, Maven Wrapper inclus, Node 24 compatible Angular 21 et Docker Desktop démarré, en mode conteneurs Linux.
Les dépendances Maven/npm et images Docker nécessitent Internet au premier lancement.

## Lancer
Depuis la racine :
```powershell
./tools/start-local.ps1
```
Le script crée des secrets uniques dans .env si ce fichier n'existe pas, vérifie backend/frontend, construit les images et lance les services.
Ouvrir http://localhost:4300 avec le nom localhost (les redirections OIDC sont limitées à cette origine).

Comptes locaux :
| Identifiant | Rôle | Mot de passe dans .env |
|---|---|---|
| rachid.owner | Propriétaire, tous les sites | RH_OWNER_PASSWORD |
| ibrahim | Caissier, magasin M01 pour la démonstration | RH_CASHIER_PASSWORD |
| rachid.stock | Responsable stock, dépôt pour la démonstration | RH_STOCK_PASSWORD |

Ces affectations sont des fixtures à confirmer. Les noms réels des magasins restent à renseigner.
Les secrets ne sont jamais imprimés par les scripts et ne doivent pas être commités ou partagés. Les lire localement dans .env.
Keycloak : http://localhost:8180 ; administration locale rh-admin, mot de passe RH_KEYCLOAK_ADMIN_PASSWORD.

## Relancer sans reconstruire
```powershell
docker compose up -d --wait
docker compose ps
```

## Arrêter sans effacer les données
```powershell
docker compose stop
```
Ne pas supprimer les volumes pour un simple redémarrage.

## Vérifier
```powershell
./tools/verify-backend.ps1
cd frontend/rh-web
npm.cmd ci
npm.cmd test -- --watch=false
npm.cmd run build
npx.cmd playwright test
```
Les tests backend d'intégration lancent un PostgreSQL jetable via Testcontainers. Les tests navigateur demandent le Compose RH déjà lancé et Google Chrome installé ; aucune donnée de production nécessaire.
Les traces Playwright sont désactivées pour éviter d'y enregistrer les saisies d'identifiants. Les captures de l'espace connecté ne contiennent pas de mot de passe.
Les pages du rapport et captures sont dans frontend/rh-web/playwright-report et test-results.

## Structure
- services/gateway-bff : session OIDC et TokenRelay.
- services/administration-service : établissements et accès.
- services/inventory-service : catalogue, stock et transferts, base PostgreSQL dédiée.
- frontend/rh-web : interface Angular.
- infra : conteneurs, reverse proxy et configuration d'identité locale.
- contracts : contrat API et évolution des événements.
- docs : architecture, décisions et point de reprise.
- tools : démarrage et initialisation locale.
Les autres services métier seront ajoutés selon la conception V2.

## Développement local
Le parcours accepté se teste par Compose sur localhost:4300. Le serveur Angular seul est destiné aux tests UI ; sans backend et configuration OIDC compatibles, il ne remplace pas le lancement complet.
Les sources sont indépendantes des secrets .env et des volumes Docker.
Le générateur de fichiers tools/write-files.ps1 est un outil local de développement, pas une API ; il n'est jamais monté ni exécuté par l'application.

## Limites de ce jalon
Compose est un environnement de développement avec HTTP local et Keycloak start-dev.
Les sessions BFF sont en mémoire : redémarrer le BFF nécessite une nouvelle connexion.
La configuration de production (HTTPS, cookies Secure, stockage des sessions si réplication, sauvegardes et restauration) reste à livrer avant utilisation commerciale.
Les données de démonstration ne se chargent qu'avec le profil local.
Modifier .env après import initial ne change pas automatiquement les mots de passe déjà enregistrés dans PostgreSQL/Keycloak. Toute rotation doit être appliquée dans les services concernés, sans effacer les volumes.

## Prochaine étape
Terminer la recette navigateur Inventory et sa documentation, puis achats et ventes. Voir docs/STATUS.md et docs/architecture/conception-v2.md.

Sur certains JDK Windows, définir RH_JAVA_SOCKET_TMP dans .env vers un répertoire local existant au chemin court corrige la création des sockets AF_UNIX. Le lanceur transmet ce réglage uniquement aux tests, puis restaure JAVA_TOOL_OPTIONS.
