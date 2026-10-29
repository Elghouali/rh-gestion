# ADR 003 — Fournisseurs et achats contrôlés
29 octobre 2026 — accepté.

## Propriété
Finance possède l'identité fournisseur dans rh_finance (rôle SQL et secret distincts). Inventory possède commandes, réceptions, contrôles et stock dans rh_inventory. La commande conserve un snapshot du nom fournisseur et des variantes/coûts. UUID fournisseur sans FK ni accès SQL interservice ; lookup HTTP avec bearer, timeout et validation d'identité.
Le module Finance livré ici est un registre de fournisseurs, pas encore un grand livre financier.

## Approbation et états physiques
OWNER crée/modifie/archive les fournisseurs et engage une commande. Les seuils métier non configurés ne permettent aucune délégation implicite.
OWNER ou STOCK_MANAGER affecté au site reçoit physiquement : QUARANTINE augmente, SELLABLE reste inchangé. OWNER seul contrôle et approuve : QUARANTINE diminue, SELLABLE augmente de l'accepté, REJECTED augmente du refusé. Aucun contrôle manager délégué n'est implémenté.
Le refus reste physiquement isolé. Il ne diminue pas automatiquement la quantité reçue fournisseur et ne crée ni remboursement ni commande de remplacement.
La commande est OPEN, PARTIAL, RECEIVED (réception physique complète), ou CLOSED (reliquat non reçu clôturé). Une commande clôturée conserve ses réceptions et reste contrôlable.

## Cohérence
Une commande ne change aucun stock. Quantités et coûts de ses lignes sont figés. L'identité d'une variante est verrouillée dès sa première commande fournisseur, même avant mouvement.
Réception et contrôle partiels sont atomiques avec journal, compteurs, clé idempotente et outbox locale. Lock du header commande, puis variantes en ordre lexicographique UUID.toString (identique aux transferts), puis soldes ordonnés. Dépassements refusés ; contraintes SQL et tests de concurrence.
Documents de réception/inspection et journaux sont immuables. Triggers protègent les snapshots des commandes et lignes ; seuls les compteurs/projections de traitement évoluent. Motif, auteur et horodatage sont enregistrés.
Un replay autorisé d'une commande déjà persistée retourne sa réponse sauvegardée, sans dépendre de l'état actuel du fournisseur ou de Finance. Les nouvelles commandes exigent un fournisseur actif. Les droits de site sont toujours revérifiés.

## Confidentialité et limites
Supplier contacts et coûts des achats réservés OWNER. STOCK_MANAGER reçoit les détails physiques de ses sites, jamais unitCost. CASHIER ne consulte pas les achats.
Pas de facture fournisseur, dette, paiement, TVA, CUMP, retour fournisseur ou publication RabbitMQ dans ce jalon. Ces flux seront développés séparément avec règles métier confirmées.
