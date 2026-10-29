# Point de reprise RH
29 octobre 2026.

## Dernier jalon — achats et réceptions fournisseurs
Catalogue/stock du dépôt et quatre magasins conservés. Finance possède désormais un registre fournisseurs dédié ; Inventory gère commandes, réceptions physiques en QUARANTINE, contrôles OWNER vers SELLABLE/REJECTED et clôture du reliquat.
UI FR/AR/EN, recherche/pagination, stock quarantaine/refusé, snapshots immuables, idempotence, audit et accès par site. Aucun stock d'ouverture, coût inconnu, solde ou réglage légal inventé.

## Vérifications
98 tests backend, 58 Angular, build production réussi. Recette achats en Chrome et contrôles de permissions/RTL réussis ; six parcours navigateur Chrome réussis sur les images finales. Détails : docs/acceptance/purchasing.md.
JaCoCo : Inventory 354/365 lignes, Finance 50/56. Angular 97,36 % lignes. Revues Java/frontend/infra faites ; défauts reproduits en RED puis corrigés (approbation, identité, locks, replay Finance et faux état vide).

## Reprise
1. Lire README.md, conception-v2.md, ADR 002 et ADR 003.
2. ./tools/start-local.ps1 génère seulement les secrets manquants et prépare Inventory/Finance sans effacer les volumes. Sept services sur Compose ; URL http://localhost:4300.
3. Phase suivante : commandes/ventes gros-détail, puis factures/dettes/règlements fournisseurs et clients avec règles confirmées. Ne pas traiter la réception physique comme une facture ou une dette.
4. Restent : retours/remplacements fournisseurs, publisher outbox/inbox, CUMP, IMEI, A4 achats, imports papier, sauvegarde/restauration et production HTTPS.
5. Préserver .env, realm et volumes. .vscode/settings.json reste un réglage local non commité.
6. Fixtures : owner tous sites, Ibrahim M01 en consultation stock, Rachid stock dépôt en réception physique. Seul OWNER approuve la sortie de quarantaine. Les noms réels et comptages restent à fournir.

## Documentation
contracts/openapi/inventory.json et purchasing.json ; docs/guides/inventory.md et purchasing.md ; docs/acceptance/inventory.md et purchasing.md.
Les données [DEMO E2E] sont locales et ne sont pas du stock réel.

## Points métier ouverts
Entité juridique, affectations réelles, IMEI, garanties, seuils d'approbation, régime fiscal et modalités financières. Pas de délégation implicite d'approbation de stock.
