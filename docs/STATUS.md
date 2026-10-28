# Point de reprise RH
28 octobre 2026.

## Jalon en cours — catalogue et stock
Code implémenté : Inventory Spring Boot avec PostgreSQL/Flyway dédié, catalogue modèles et variantes couleur/RAM/stockage, prix gros/détail, stock par dépôt et quatre magasins, ouverture comptée (zéro accepté), ajustements motivés, transferts en transit, réceptions partielles et pertes réservées au propriétaire.
Interface Angular FR/AR/EN, recherche et pagination, rôles et affectations contrôlés côté serveur, journal immuable et commandes idempotentes.
Aucune quantité initiale réelle n'est inventée ; un emplacement non compté reste non initialisé. Le coût d'ouverture peut rester inconnu. La valorisation/CUMP reste à développer.

## Vérification obtenue
- 68 tests backend réussis : Administration 23, Gateway 6, Inventory 39 (33 intégration PostgreSQL + 6 client HTTP Administration).
- Couverture lignes JaCoCo Inventory : 255/260.
- 18 tests Angular réussis et build réussi.
- Revues Java et frontend sans défaut matériel restant signalé.
- Six conteneurs locaux démarrés et sains lors du dernier contrôle.
- Le nouveau parcours Playwright stock échoue actuellement à la sélection du modèle dans le formulaire de variante (e2e/inventory.spec.ts:60). Le modèle apparaît dans la capture mais le locator imbriqué ne le trouve pas. Le parcours complet transfert/réception n'est donc pas encore validé dans le navigateur. Les quatre parcours OIDC du socle avaient été validés au jalon précédent.

## Reprise prioritaire
1. Corriger le locator ou le formulaire du sélecteur modèle après inspection DOM, puis terminer le parcours navigateur stock complet.
2. Renforcer les tests Angular des écritures et du retry ; relancer tests/build et Playwright.
3. Compléter contrat OpenAPI Inventory, ADR et documentation utilisateur ; mettre à jour les textes du tableau de bord qui parlent encore du stock à venir.
4. Commerce, achats, crédit, caisse, fournisseurs et SAV restent à développer. Les événements outbox sont persistés mais pas encore publiés.

## Lancement et conservation des données
Lire README.md et docs/architecture/conception-v2.md. Lancer ./tools/start-local.ps1 depuis la racine ; le script ajoute la base Inventory sans supprimer les bases existantes. Les secrets restent dans .env ignoré par Git. Ne pas effacer les volumes, .env ou le realm pour corriger une erreur de code.
Les comptes et affectations sont des fixtures locales à confirmer : propriétaire tous sites, Ibrahim caissier M01 en lecture stock, Rachid stock dépôt. Les créations [DEMO E2E] locales sont des données de test.

## Questions métier ouvertes
Entité(s) juridique(s), rôle exact du second Rachid, vendeurs par site, IMEI, garanties, seuils d'approbation et régime fiscal. Pas de réglages légaux inventés.