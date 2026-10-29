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

Le module catalogue et stock est maintenant implémenté : variantes couleur/RAM/stockage, stock par site, ouvertures, ajustements et transferts avec réception partielle. La recette locale est validée : 68 tests backend, 42 tests Angular et cinq parcours navigateur ; voir docs/acceptance/inventory.md. Ventes, crédits, caisse et SAV restent à développer.

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
Développer les commandes et ventes, puis les flux financiers et retours fournisseurs. Voir docs/STATUS.md et docs/architecture/conception-v2.md.

Sur certains JDK Windows, définir RH_JAVA_SOCKET_TMP dans .env vers un répertoire local existant au chemin court corrige la création des sockets AF_UNIX. Le lanceur transmet ce réglage uniquement aux tests, puis restaure JAVA_TOOL_OPTIONS.

## Catalogue et stock
Guide opérateur : [docs/guides/inventory.md](docs/guides/inventory.md).
Décisions : [ADR 002](docs/decisions/002-inventory.md). Contrat : [OpenAPI Inventory](contracts/openapi/inventory.json).

| Rôle | Opérations de ce jalon |
|---|---|
| OWNER | Catalogue, ouverture, ajustement, transfert, réception et perte en transit |
| STOCK_MANAGER | Consultation de ses sites, expédition depuis une source affectée, réception dans une destination affectée |
| CASHIER | Consultation de ses sites, sans coûts d'achat ni écritures stock |

Le stock non compté reste non initialisé. Le zéro doit être confirmé par comptage. Les coûts peuvent rester inconnus ; aucune valorisation comptable n'est calculée. Les transferts restent en transit jusqu'à réception (partielle possible). Un journal ne se modifie pas : les corrections sont de nouveaux mouvements.
Le script start-local ajoute les secrets manquants et crée la base Inventory sur une installation existante via tools/ensure-inventory-db.ps1, sans supprimer les volumes. Les événements outbox sont persistés ; leur publication n'est pas encore implémentée.
## Achats et réceptions fournisseurs
Module livré : registre fournisseur dans Finance (base dédiée), commandes dans Inventory, réception physique en quarantaine, contrôle du propriétaire vers disponible/refusé et clôture du reliquat non reçu. Les coûts et contacts fournisseurs sont réservés au propriétaire ; un responsable réceptionne uniquement sur ses sites. Les écritures sont idempotentes et auditées.
Guide : [purchasing.md](docs/guides/purchasing.md), [ADR 003](docs/decisions/003-purchasing.md), [OpenAPI](contracts/openapi/purchasing.json) et [recette](docs/acceptance/purchasing.md).
Le lancement prépare désormais aussi rh_finance sans effacer les volumes ni les secrets existants. Sept services sont lancés. Aucune facture, dette, paiement ou valorisation n'est encore calculé. Les unités refusées attendent un futur traitement retour/remplacement fournisseur.