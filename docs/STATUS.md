# Point de reprise RH
29 octobre 2026.

## Jalon validé — catalogue et stock
Socle OIDC/BFF, Administration et Inventory Spring Boot, Angular FR/AR/EN, PostgreSQL et Keycloak.
Catalogue modèles/variantes couleur-RAM-stockage, prix gros/détail, archivage, recherche/pagination. Stock du dépôt et des quatre magasins, ouverture comptée (zéro accepté), ajustements motivés, transferts en transit, réceptions partielles et pertes OWNER. Journal immuable, version optimiste et commandes idempotentes.
Aucune quantité réelle inventée : emplacement non compté = non initialisé. Coût inconnu reste inconnu ; aucune valorisation/CUMP.

## Vérifications du 29 octobre
- Maven verify : 68 tests réussis (Administration 23, Gateway 6, Inventory 39).
- Inventory JaCoCo : 255/260 lignes, soit 98,08 %.
- Angular : 42 tests réussis ; couverture 97,58 % lignes, 91,90 % statements, 88,32 % branches. Le compteur global fonctions est 65,62 %, incluant les fonctions générées du template ; aucune affirmation de 80 % sur ce compteur.
- Build Angular production réussi, bundle initial 293,18 kB.
- Cinq parcours Playwright Chrome réussis sur le Compose local final, dont catalogue/stock/transfert/réceptions partielles, permissions et RTL.
- Revues Java et frontend effectuées ; remarques sur fixtures et isolation des clés de retry corrigées.
- Les six conteneurs sont sains. Aucune suppression de volume ni rotation de secret.
Détails : docs/acceptance/inventory.md.

## Reprise
1. Lire README.md, ce fichier, conception-v2.md et ADR 002.
2. Lancer ./tools/start-local.ps1 puis vérifier docker compose ps.
3. Prochaine phase proposée : achats/réceptions fournisseurs, puis commandes et ventes gros/détail. Finaliser les règles métier avant les flux crédit/caisse.
4. Publisher outbox, inbox, CUMP, IMEI, imports papier, sauvegarde/restauration de production et déploiement HTTPS restent à livrer.
5. Ne pas supprimer .env, le realm importé ou les volumes. Les comptes sont des fixtures : owner tous sites, Ibrahim M01 en lecture stock, Rachid stock dépôt.
6. Les tests navigateur créent des données locales clairement nommées [DEMO E2E]. Ne pas les importer comme stock réel.

## Contrats et guides
- contracts/openapi/inventory.json
- docs/decisions/002-inventory.md
- docs/guides/inventory.md

## Questions métier ouvertes
Entité(s) juridique(s), affectation exacte du second Rachid, vendeurs, IMEI, garanties, seuils d'approbation et régime fiscal. Les noms réels des magasins et les comptages restent à fournir. Aucun réglage légal inventé.
