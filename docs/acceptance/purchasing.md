# Recette locale — achats et réceptions fournisseurs
29 octobre 2026.

## Validation
- Maven verify : 98 tests réussis : Administration 23, Gateway 6, Inventory 62, Finance 7.
- Inventory inclut 33 tests stock/catalogue, 16 achats/concurrence, 6 HTTP Administration et 7 HTTP Finance.
- JaCoCo lignes : Inventory 354/365 (96,99 %), Finance 50/56 (89,29 %).
- Angular : 58 tests ; couverture 97,36 % lignes, 90,73 % statements, 87,21 % branches. Fonctions globales 67,57 % avec templates générés ; aucune revendication de 80 % sur ce compteur.
- Build production Angular réussi. Sept conteneurs locaux : PostgreSQL, Keycloak, Administration, Finance, Inventory, Gateway et web.
- Nouveau parcours Chrome fournisseur -> commande -> quarantaine -> contrôle OWNER -> refus isolé -> clôture exécuté avec succès. Suite complète : six parcours Chrome réussis sur les images finales (OIDC, permissions, catalogue/stock et achats).

## Scénarios importants
Commande sans stock ; coût et libellés figés ; identité variante verrouillée avant première réception ; manager limité à son site sans prix ; caissier refusé ; contrôle manager refusé côté API et UI.
Réceptions/contrôles partiels, dépassements interdits, duplication par replay impossible, concurrence sur dernières unités, clôture préservant les contrôles. Ordre des locks compatible avec transferts testé sur UUID de signes différents.
Fournisseur archivable, version périmée refusée, unicité normalisée, mauvais bearer/ID et erreur Finance traités sans accès par défaut. Replay réussi pendant panne Finance.
Échec de lecture UI ne s'affiche plus comme liste vide, ni comme fournisseurs périmés. Recherches indépendantes et reprise après erreur testées.

## Parcours réel
Propriétaire : fournisseur de démonstration, téléphone noir 8/128, commande cinq unités coût 70,1250 DH.
Responsable dépôt : trois unités reçues -> QUARANTINE 3, disponible 0 ; coûts omis ; contrôle direct refusé403.
Propriétaire : accepte2/refuse1 -> disponible2, quarantaine0, refusé1. Clôture des deux unités non reçues ; nouvelle réception désactivée. Journal et affichage arabe RTL vérifiés.

Les tests ne touchent qu'à l'environnement local et créent des données [DEMO E2E]. Aucune dette, caisse ou valeur comptable fictive. Les résultats CI GitHub restent distincts de cette recette locale.
