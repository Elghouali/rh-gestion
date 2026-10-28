# RH Gestion — Conception V2 : Spring Boot, microservices et Angular
28 octobre 2026 — Document technique de référence

## 1. Décision et portée

L'application sera construite avec un frontend Angular et plusieurs services Spring Boot déployables indépendamment.

Cette V2 remplace les choix techniques de la V1 concernant Laravel, React, Inertia, le partage de base et les transactions globales. Les besoins fonctionnels RH restent ceux du cahier des charges et de la V1 : un dépôt, quatre magasins, gros avec retrait client, détail, crédit, fournisseurs à terme, caisse, SAV, trois langues et A4.

Les noms des services décrivent des responsabilités ; ils ne désignent pas un service par écran ou par table. Le découpage ci-dessous constitue notre point de départ.

Les points métier encore ouverts restent : entité(s) juridique(s), affectation exacte du second Rachid, comptes des vendeurs, IMEI obligatoire ou non, seuils, garanties, régime fiscal, volumes et soldes d'ouverture.

Aucun projet applicatif n'a encore été généré. Ce document définit sa construction.

## 2. Stack retenue

| Couche | Choix |
|---|---|
| Backend | Java LTS compatible, Spring Boot, Spring MVC |
| Architecture interne | Controller -> application service -> domaine -> repository/adapters |
| Persistance | Spring Data JPA et SQL explicite pour les écritures/verrous nécessaires |
| Migrations | Flyway, migrations propres à chaque service |
| Validation | Jakarta Bean Validation ; invariants dans le domaine |
| Frontend | Angular et TypeScript, composants standalone, lazy routes |
| UI | Angular Material/CDK, thème RH, formulaires réactifs et RTL |
| État frontend | Signals pour état local, RxJS pour flux asynchrones ; pas de grand store global par défaut |
| Authentification | Keycloak OIDC ; Spring Security |
| Entrée API | Spring Cloud Gateway avec rôle BFF et sessions serveur |
| Données | PostgreSQL, base et compte SQL propres à chaque service |
| Messages | RabbitMQ, transactional outbox et inbox |
| Contrats | OpenAPI pour HTTP ; schémas JSON versionnés pour événements |
| Tests | JUnit, tests Spring, Testcontainers, tests Angular, Playwright |
| Observabilité | Actuator, Micrometer/OpenTelemetry, logs structurés |
| Exécution locale | Docker Compose et Maven Wrapper |
| PDF | Modèles HTML, Chromium et police arabe embarquée |

Les numéros précis de Java, Spring Boot, Spring Cloud, Angular et Node seront fixés au scaffolding après vérification de compatibilité ; aucune préversion nécessaire.
Spring Cloud doit suivre la matrice de compatibilité avec Spring Boot :
https://spring.io/projects/spring-cloud/
Angular doit suivre sa matrice Node/TypeScript/RxJS :
https://angular.dev/reference/versions
https://angular.dev/reference/releases

Nous utilisons Docker Compose au départ. Kubernetes, Eureka, Config Server et Kafka ne sont pas requis pour cette première architecture.

## 3. Vue d'ensemble

Navigateur
  -> Angular
  -> HTTPS / Spring Cloud Gateway BFF
      -> Administration
      -> Inventory
      -> Commerce
      -> Finance
      -> Insights & Documents

Keycloak authentifie les utilisateurs.
Chaque service métier est un Resource Server Spring Security.
RabbitMQ transporte les événements après commit local.
Chaque service possède sa base PostgreSQL.
Les documents et justificatifs sont stockés dans un stockage privé.

En local, un même serveur PostgreSQL peut héberger les différentes bases avec comptes et droits séparés. Cela ne donne jamais le droit de faire des jointures SQL ou des écritures entre services.

Un seul dépôt Git facilite notre travail ; chaque service conserve son build, ses migrations et son image indépendants.
Un service web Angular unique est suffisant.

## 4. Responsabilités et propriété des données

### 4.1 Identity : Keycloak
Responsable des identifiants de connexion, mots de passe, authentification renforcée, sessions d'identité et clients OIDC.
Pas de table de mots de passe maison dans les services métier.
Les profils métier référencent le subject OIDC stable, pas un prénom.

### 4.2 Administration service — rh_admin
- Organisation RH, établissements et identité légale.
- Affectations utilisateur/établissement et permissions métier.
- Paramètres de seuils, langue et configuration.
- Vue agrégée « À valider », sans devenir propriétaire de toutes les décisions.
- Historique des modifications administratives.

Les approbations de crédit sont décidées et persistées dans Finance.
Les approbations de réception/ajustement sont décidées dans Inventory.
Les prix négociés et dérogations commerciales sont contrôlés dans Commerce, avec vérification des contraintes financières par Finance.

Administration est l'autorité des affectations ; les services contrôlent eux-mêmes leur périmètre.
Pour une opération sensible, ils vérifient la version d'autorisation actuelle auprès d'Administration et refusent si la vérification est indisponible. Les lectures peuvent utiliser un cache à durée courte documentée. La révocation invalide les caches et sessions concernées ; son délai effectif maximal est testé.
Le gateway ne constitue pas à lui seul une barrière d'autorisation métier.

### 4.3 Inventory service — rh_inventory
- Catalogue, variantes, tarifs standards et références.
- IMEI/numéros de série si activés.
- Emplacements, stock, réservations et compartiment d'attente de retrait.
- Journal et valorisation.
- Achats/commandes fournisseurs et réceptions physiques.
- Réapprovisionnement des magasins, transit et écarts.
- Inventaires, pertes et retours physiques.
- Autorité de la remise physique au client.

Données principales : products, product_variants, serial_identifiers, inventory_locations, stock_movements, stock_entries, stock_balances, stock_reservations, valuation_balances, purchase_orders, goods_receipts, transfers, inventory_counts, handovers.

Les fournisseurs sont référencés par leur identifiant Finance. Les coordonnées nécessaires sur les commandes sont figées en snapshot.

### 4.4 Commerce service — rh_commerce
- Clients et données de contact commerciales.
- Paniers, commandes détail/gros, négociation et préparation.
- Commandes avec remises partielles.
- Orchestration persistante des parcours de vente et de retour.
- Demandes de retour/échange.
- SAV, preuve de garantie, chaîne de garde des biens clients.

Données principales : customers, sales_orders, sales_order_lines, fulfillment_requests, checkout_workflows, workflow_steps, return_requests, service_cases, custody_events.

Commerce ne modifie ni le stock, ni le solde client, ni la caisse.
Les clients sont référencés par Finance, avec snapshot de l'identité légale nécessaire.
Une projection de commande ne prouve pas qu'un client a effectivement retiré sa marchandise.

### 4.5 Finance service — rh_finance
- Identité et comptes des fournisseurs.
- Comptes financiers clients liés aux identifiants Commerce.
- Factures de vente, avoirs et numérotation légale.
- Factures fournisseurs et rapprochement aux réceptions.
- Paiements, affectations, acomptes, remboursements.
- Créances, dettes, échéances et réservations d'exposition crédit.
- Approbations de crédit.
- Caisses, clôtures, dépenses et transferts d'argent.

Données principales : suppliers, party_accounts, account_entries, sales_invoices, supplier_invoices, credit_notes, payments, payment_allocations, financial_preparations, credit_holds, approval_requests, cash_sessions, cash_entries, expenses, money_transfers, document_sequences.

Finance est l'unique autorité du solde et de l'acceptation financière d'une remise.
Les coordonnées et montants d'une facture émise sont figés, même si le client change de nom ensuite.

### 4.6 Insights & Documents service — rh_insights
- Rapports consolidés et projections de consultation.
- Alertes informatives.
- Exports CSV/Excel.
- Rendu des PDF A4, photos et pièces jointes.
- Historique des générations et statut de fraîcheur des rapports.

Ce service ne calcule pas une disponibilité autorisant une vente, n'approuve pas un crédit et ne crée pas un numéro de facture.
Finance fournit le snapshot de facture déjà émise ; Documents le met en page.
Une panne de PDF n'annule pas une facture.

### Données techniques communes, mais locales
Chaque service possède ses propres outbox_events, inbox_events, idempotency_records et audit_events.
Les bibliothèques communes contiennent uniquement l'observabilité, les erreurs ou les conventions techniques ; aucune entité JPA métier partagée.

Les IDs interservices sont des références contractuelles, sans clés étrangères entre bases.
Dans chaque base, clés étrangères et contraintes locales restent obligatoires.

## 5. Conception interne et dépôt

rh-gestion/
  frontend/rh-web/
  services/
    gateway-bff/
    administration-service/
    inventory-service/
    commerce-service/
    finance-service/
    insights-documents-service/
  contracts/
    openapi/
    events/
  infra/
    compose/
    keycloak/
    observability/
  docs/
    architecture/
    decisions/
    runbooks/
    acceptance/
  plans/

Dans un service métier :
  configuration/
  api/                 controllers, DTO et validation
  application/         cas d'usage et orchestration locale
  domain/              règles, états et interfaces nécessaires
  infrastructure/      JPA, SQL, HTTP, RabbitMQ et stockage
  src/main/resources/db/migration/
  src/test/

La structure est organisée par fonctionnalité lorsque le service grandit.
Injection par constructeur, DTO distincts des entités, BigDecimal pour l'argent, Instant pour les dates techniques, LocalDate pour les dates métier appropriées.
Les contrôleurs n'accèdent pas directement aux repositories des autres fonctionnalités.

## 6. Transactions et communication

### HTTP
Utilisé pour commandes nécessitant une réponse immédiate et lectures auprès de l'autorité métier :
- réserver un produit ;
- préparer un engagement financier ;
- consulter le résultat d'une commande ;
- confirmer une remise.

Chaque client HTTP a un timeout borné. Les retries automatiques concernent uniquement les opérations idempotentes.
Un timeout signifie « résultat inconnu » : on interroge le résultat avec la même clé, on n'invente pas un échec.

### Événements RabbitMQ
Utilisés pour propager les changements, alimenter rapports et poursuivre les workflows.

Écriture métier et outbox sont commitées dans la même transaction PostgreSQL locale.
Un relais publie avec confirmation broker puis marque l'événement envoyé.
Un crash peut republier le même événement : les consommateurs doivent être idempotents.
Le consommateur traite inbox et changement métier dans sa transaction, puis acquitte le message.

Enveloppe :
eventId, eventType, schemaVersion, aggregateId, aggregateVersion,
occurredAt, organizationId, correlationId, causationId, payload.

Les événements incluent les données minimales nécessaires, sans secrets.
Les IDs métier et versions permettent de détecter doublons, trous et désordre.
Une projection ne suppose pas que RabbitMQ garantit à lui seul l'ordre par agrégat.
Elle ignore les anciennes versions, détecte les versions manquantes et se resynchronise auprès du propriétaire.
Les messages durablement en erreur vont en file de traitement manuel/DLQ, avec alerte et procédure de rejeu.

Aucune transaction distribuée SQL/XA ni promesse « exactly once ».
La cohérence globale se construit par transactions locales, idempotence, workflow persistant et rapprochement.

## 7. Parcours critique : vente et remise

Le parcours est orchestré par Commerce, qui persiste état, étape, version de commande, tentatives, IDs participants, résultat connu et prochaine action.

1. DRAFT : figer client, établissement, prix proposés et version de commande.
2. STOCK_RESERVED : Inventory réserve les quantités et numéros de série concernés.
3. FINANCE_PREPARED : Finance valide et fige prix, arrondis, fiscalité, identité, paiement/crédit et engagement financier.
4. STOCK_STAGED : Inventory déplace les unités vers PENDING_HANDOVER, non disponibles pour une autre vente mais encore physiquement chez RH.
5. INVOICED : Finance émet la facture idempotente et affecte l'avance ou transforme l'engagement crédit en créance sans double comptage.
6. READY_FOR_HANDOVER : Commerce affiche que les conditions sont réunies.
7. HANDED_OVER : l'employé confirme la remise dans Inventory ; une transaction locale effectue la sortie définitive, fige coût de sortie, quantité, heure et auteur.
8. COMPLETED : Commerce et Finance intègrent la confirmation ; les rapports distinguent facturé, remis et encaissé.

La règle de reconnaissance du chiffre d'affaires comptable reste à valider avec le comptable ; les rapports opérationnels n'assimilent pas automatiquement facture, remise et encaissement.
Le coût des ventes est reconnu à la sortie physique validée ; la marchandise staged reste du stock RH non disponible.
Le transfert de propriété juridique suit les conditions commerciales validées ; il n'est pas déduit d'un simple statut technique.

La dernière confirmation est idempotente. Si le client a reçu l'article et que le réseau coupe, la reprise retrouve le même résultat ; elle ne refait pas une sortie.
Une remise manuelle réellement effectuée malgré indisponibilité exige une procédure exceptionnelle tracée et une régularisation, sans bouton « réussi » trompeur.

### Réservations
ACTIVE -> PREPARED -> STAGED/RELEASED.
ACTIVE peut expirer après une durée paramétrée.
Après préparation financière, aucune expiration autonome : l'orchestrateur décide d'une libération.
STAGED ne disparaît jamais par nettoyage TTL.
Chaque préparation possède une API de consultation et des rapprochements d'orphelins.

### Comptant
Finance n'autorise pas le retrait sur la seule indication « payé » envoyée par Angular.
Les espèces réellement reçues sont enregistrées comme avance affectable avant émission finale.
Un crédit approuvé est nécessaire pour toute partie non réglée.

### Crédit
L'exposition comprend créances ouvertes et engagements réservés non facturés, diminués des crédits/avances applicables, sans double comptage.
Finance verrouille le compte client et réserve l'exposition avant validation.
Accord lié à la version de commande, au montant et aux conditions.
Toute modification sensible exige revalidation.
Expiration et révocation concernent les engagements non encore consommés.

### Compensations
- Avant préparation financière : libérer la réservation.
- Après paiement d'avance : l'avance reste enregistrée ; remboursement distinct autorisé.
- Après staging, avant facture : réintégrer les unités non remises par opération compensatrice.
- Après facture, sans remise : annulation commerciale et avoir autorisé, puis libération contrôlée du staging.
- Après remise : retour physique vérifié, avoir et remboursement/crédit selon le cas.
- Sur timeout : consulter et reprendre ; aucun remboursement automatique.
- Après une étape irréversible : terminer les écritures manquantes ou ouvrir un dossier de résolution.

Un écran « Opérations à reprendre » montre les cas bloqués, les états des participants et les actions autorisées.

## 8. Autres parcours distribués

### Achats
Inventory possède commande, réception, propriété physique et coût.
Finance possède facture fournisseur, dette et règlement.
Chaque ligne financière est rapprochée des IDs de réception et quantités correspondants.
Une réception non facturée et une facture non réceptionnée sont signalées séparément.
Le coût provisoire et ses ajustements sont explicites, jamais remplacés silencieusement par zéro.
Les approbations de gros achat/dépense sont vérifiées avant engagement selon seuils ; les achats sans seuil configuré exigent le propriétaire.
La validation de réception dans Inventory libère la quarantaine après contrôle.
Les événements rejoués ne créent pas une deuxième dette.

### Transferts internes
Tout le parcours de stock reste dans Inventory : demande, préparation, expédition, transit, réception et écarts.
Un transfert entre magasins d'une même entité n'appelle pas une vente Finance.
Si plusieurs entités juridiques sont confirmées, cadrage intersociétés supplémentaire obligatoire.

### Retours
Commerce crée la demande liée à vente/remise.
Inventory contrôle quantité physiquement revenue et état.
Finance émet l'avoir et traite crédit/remboursement séparément.
Une panne après réception du retour n'efface pas son existence ; le workflow attend le traitement financier.
Le retour cumulé ne dépasse pas les remises éligibles ; sérialisation contrôlée si activée.

### SAV
Commerce possède la garde des biens clients et leurs accessoires.
Ces biens ne sont jamais ajoutés au stock possédé Inventory.
Un échange SAV appelle le parcours de retour puis une nouvelle remise.
Finance traite la différence financière éventuelle ; Inventory identifie ancien et nouvel appareil.
Les preuves papier sont acceptables après contrôle manuel tracé ; une garantie incertaine reste « À déterminer ».

### Inventaire
Comptage et blocage du périmètre, ajustement, coût et approbation restent atomiques dans Inventory.
Aucune écriture Finance directe depuis un écran d'inventaire.
Les rapports de valorisation se mettent à jour via événements et contrôlent leur retard.

## 9. API, sécurité et frontend

API externes via gateway : /api/v1/...
API interservices séparées : /internal/v1/... avec authentification service et contrôle du contexte.
Le navigateur ne peut pas appeler les ports internes.

Exemples :
- GET /api/v1/catalog/variants
- POST /api/v1/purchase-orders
- POST /api/v1/goods-receipts/{id}/submit
- POST /api/v1/goods-receipts/{id}/approve
- POST /api/v1/transfers/{id}/dispatches
- POST /api/v1/checkouts
- GET /api/v1/checkouts/{id}
- POST /api/v1/handovers/{id}/confirm
- POST /api/v1/payments
- POST /api/v1/credit-approvals/{id}/decision
- POST /api/v1/service-cases
- GET /api/v1/documents/{id}
- GET /internal/v1/operations/{idempotencyKey}

Commandes sensibles : Idempotency-Key et expectedVersion.
Réponses : 400 validation, 401 non connecté, 403 droit refusé, 409 conflit métier/version.
Un traitement asynchrone répond 202 avec operationId et URL de suivi.
Erreurs structurées ProblemDetail, code métier stable traduit par Angular.
Le serveur recalcule prix, quantités, arrondis et taxes.
Les contrats OpenAPI génèrent les types clients Angular ; aucun partage d'entités JPA.

### Session
Le BFF réalise OIDC et conserve les tokens côté serveur.
Angular utilise un cookie de session HttpOnly, Secure et SameSite approprié, avec protection CSRF.
Pas de token dans localStorage.
Le BFF relaie un access token destiné aux services ; issuer, audience et expiration sont validés.
Les tâches de fond utilisent une identité technique limitée et conservent l'auteur métier.
Les en-têtes utilisateur/établissement fournis par le navigateur ne sont pas une preuve de droit.

### Frontend
app/core : session, HTTP, erreurs, guards.
app/shared : tableaux, monnaie, sélecteurs, confirmations, composants RTL.
app/features : dashboard, catalog, stock, purchases, transfers, sales, customers, finance, returns, service, approvals, reports, administration.
Formulaires réactifs, pagination serveur, accès clavier, écran de vente rapide.
FR/AR/EN ; RTL ; textes et dates cohérents ; design desktop utilisable sur tablette.
Les guards servent à la navigation ; les services refusent aussi les accès interdits.
Un workflow en attente reste « en cours » avec état réel, sans faux succès.
La remise au client exige l'état autorisé, pas seulement un PDF disponible.

## 10. Rapports et impression

Insights consomme les événements et conserve des projections de lecture.
Il affiche dernière mise à jour, retard et indicateurs incomplets.
Décisions de stock et crédit toujours auprès d'Inventory et Finance.
Les marges à coût inconnu sont indiquées incomplètes.
Exports et PDF appliquent les mêmes droits d'établissement que les écrans.
Un export massif est asynchrone et protégé.

Les modèles A4 restent ceux du cahier : factures, avoirs, préparation, transfert, réception, retrait, reçus, relevés, SAV, inventaire et caisse.
Prototype arabe avec tableaux multipages dès le socle.
Numéro légal émis uniquement par Finance ; génération ou réimpression PDF ne crée aucun numéro.
Le régime fiscal, les mentions et conditions de conservation doivent être validés avant production. La V2 ne constitue pas une certification de conformité.

## 11. Exploitation et tests

Compose local : frontend, BFF, cinq services, Keycloak, PostgreSQL et RabbitMQ.
Les composants peuvent tourner sur une même machine au départ tout en gardant isolation des données.
Production : réseaux privés, ports minimaux exposés, TLS, secrets hors Git, volumes persistants et limites de ressources.
Pas de déploiement payant sans choix d'hébergement explicite.

Tests :
- JUnit : règles, argent BigDecimal, crédit, états et valorisation.
- Testcontainers PostgreSQL et RabbitMQ : transactions, outbox, inbox, contraintes.
- Contrats HTTP/événements et compatibilité des versions.
- Playwright : réception -> transfert -> vente -> retrait -> règlement.
- Deux ventes dernière unité et deux crédits simultanés.
- Crash après chaque étape de checkout.
- Réponse perdue après succès ; retry avec même clé.
- Messages dupliqués, anciens, désordonnés et manquants.
- Panne Finance, RabbitMQ, renderer PDF et Administration.
- Réservation expirant pendant préparation.
- Deux confirmations concurrentes de remise.
- Paiement dépassé, suraffectation et caisse clôturée.
- Révocation d'un compte et accès direct aux PDF.
- Interface/PDF arabes et noms latins.
- Rapprochement stock/journal, comptes/relevés, caisse/espèces, commande/remise/facture.
- Restauration et reprise interservices.

Sauvegarder chaque base, pièces jointes et configuration d'identité. Les outbox/inbox et workflows font partie des sauvegardes.
Une restauration indépendante peut remettre les services à des instants différents : arrêter les mutations, définir le point de reprise, réconcilier participants et messages puis rouvrir.
RabbitMQ n'est pas une sauvegarde du journal métier.
Objectifs RPO/RTO à confirmer par test et budget ; aucun objectif garanti avant choix infrastructure.

Commandes cibles : ./mvnw verify, npm ci, npm run build, script tests Angular configuré, npx playwright test et docker compose up -d.
Le système de tests frontend est celui de la version Angular retenue.

## 12. Nouveau plan de construction

Tous les lots incluent code, migrations, contrat, droits, tests utiles et note de reprise. Démonstration avec données fictives avant passage au lot suivant.

| Lot | Contenu | Dépendances | Critère de sortie |
|---|---|---|---|
| 1 | Repo, Java/Maven/Angular, Compose, healthchecks | Aucune | Services démarrables et CI minimale |
| 2 | Keycloak, BFF, Administration, permissions, FR/AR/EN et prototype PDF | 1 | Connexion et accès par établissement testés |
| 3 | Contrats, outbox/inbox, idempotence, reprise workflow | 2 | Message doublé ou réponse perdue sans effet doublé |
| 4 | Inventory : catalogue, stock, coûts, ouverture, réservation | 3 | Dernière unité concurrente et rapprochement validés |
| 5 | Finance : comptes, paiements, caisse, avances, crédit et séquences | 3 | Allocation, crédit concurrent et clôture corrects |
| 6 | Achats, fournisseurs, réception/quarantaine et rapprochement financier | 4, 5 | Achat partiel réglé en plusieurs fois cohérent |
| 7 | Demandes magasins, transferts, transit et inventaires | 6 | Écarts et quantités en transit soldables |
| 8 | Commerce/Angular : vente détail et saga de remise | 7 | Vente complète et reprise à chaque panne validées |
| 9 | Commandes gros, retrait partiel, crédit et négociation | 8 | Stock, facture et solde corrects sur remises partielles |
| 10 | Retours, échanges, avoirs et SAV | 9 | Bien client distinct, échange et remboursement tracés |
| 11 | Rapports, documents complets, alertes et performances | 10 | Projections rapprochées, retard visible, A4 trois langues |
| 12 | Reprise papier, restauration, formation et pilote | 11 | Dépôt + un magasin acceptés puis généralisation |

Chaque lot reprend les invariants et modèles pertinents de la V1, avec les propriétaires de données définis ici.
Les lots 4 et 5 peuvent être développés séparément après contrats, mais nous pouvons les réaliser séquentiellement.
Une modification de contrat doit conserver une période de compatibilité ; on n'impose pas le redéploiement simultané de tous les services pour un champ supplémentaire.
Rollback : schémas compatibles et ancienne image si possible ; écritures validées corrigées par compensation, jamais par effacement de ventes réelles.
Chaque lot conserve un point de reprise : réalisé, tests, décisions ouvertes et étape suivante.

La première implémentation concrète sera le lot 1, suivi d'une connexion Angular -> BFF -> service sécurisé. Les services métier viendront ensuite ; un écran de démonstration ne sera pas présenté comme une gestion de stock terminée.
