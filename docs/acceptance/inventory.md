# Recette — catalogue et stock
29 octobre 2026. Environnement local de développement, Java 21, Spring Boot 3.5.16, Angular 21. Les données E2E sont des démonstrations explicites.

## Vérifications exécutées
- ./tools/verify-backend.ps1 : BUILD SUCCESS, 68 tests, aucune erreur/échec/skip.
- npm.cmd test -- --watch=false --coverage : 42 tests réussis. Lignes 97,58 %, statements 91,90 %, branches 88,32 %. Fonctions globales 65,62 % (templates inclus) ; Inventory TS 99,53 % lignes.
- npm.cmd run build : succès, bundle initial 293,18 kB.
- npx.cmd playwright test : 5/5 parcours réussis sur Chrome installé et la dernière image web.
- docker compose ps : administration, gateway, inventory, keycloak, postgres et web sains.
- Contrat Inventory JSON : syntaxe et operationId uniques contrôlés ; 13 chemins, 23 schémas. Contrat écrit à partir des DTO et routes implémentés, sans génération serveur.

## Preuves backend
Inventory : 33 tests avec PostgreSQL Testcontainers + 6 tests HTTP du client Administration.
Couverture Inventory JaCoCo 255/260 lignes (98,08 %).
Scénarios : unicité normalisée/null, RAM/stockage PHONE, prix invalides, ouverture zéro/unique/tardive, stock négatif interdit, idempotence et conflits de payload, permissions source/destination, journal non modifiable, coût omis hors OWNER, concurrence sur dernière unité et sur réception, rollback multi-lignes, archivage avec réception du transit et identité verrouillée.
Administration/Gateway : 29 tests, dont annuaire des destinations interdit au caissier. Les accès échouent fermés si Administration est indisponible.

## Parcours navigateur Inventory
Connexion propriétaire ; création modèle PHONE et variantes Noir 8/128, Bleu 8/256.
Comptage initial de la première variante dans les cinq sites ; seconde variante non initialisée.
Connexion responsable dépôt ; expédition de quatre unités vers M01. Vérification : dépôt disponible 10 -> 6, transit 0 -> 4, destination inchangée.
Propriétaire : réceptions de deux puis deux unités ; destination +4, transit soldé.
Vue stock sur cinq sites, capture français puis arabe RTL.
Connexion caissier : pas de commandes d'écriture, demande directe du dépôt = 403, journal M01 sans unitCost.
Rechargement et relecture des quantités persistées.

Les quatre parcours du socle (PKCE, owner/arabe/impression/logout, caissier isolé, responsable dépôt isolé) passent également.

## Preuves Angular
Formulaires création/modification/archivage, conversion virgule, téléphone incomplet rejeté, comptage zéro/coût facultatif, quantité entière, ajustement signé non nul, transfert multi-lignes, réception bornée, perte OWNER, double soumission bloquée et affichage d'erreur.
Retry : même clé après réseau/5xx, rotation après succès/409, conservation dans l'onglet et séparation des payloads/chemins. Le storage ne contient pas le payload métier.
Rendu des formulaires et journal avec coûts OWNER, retrait des coûts caissier, gestion des recherches indépendantes et variante sélectionnée hors page.

## Limites
Recette locale, pas une certification de production. Publication outbox, valorisation, achats/ventes/finance et sauvegardes restent hors jalon. GitHub CI est distinct des vérifications locales ; son résultat n'est pas attesté dans cette recette.
