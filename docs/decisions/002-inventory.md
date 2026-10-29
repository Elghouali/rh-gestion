# ADR 002 — Catalogue et stock par site
29 octobre 2026 — accepté pour le jalon Inventory.

Inventory possède sa base rh_inventory : catalogue, soldes, journal, transferts, idempotence et outbox. Administration possède les sites et affectations. Aucun SQL entre services : chaque accès relit les droits par HTTP avec le JWT utilisateur et un timeout. Une indisponibilité refuse la demande.

OWNER gère le catalogue, les ouvertures, les ajustements et les pertes. STOCK_MANAGER expédie depuis un site affecté et réceptionne à une destination affectée. CASHIER consulte ses sites. L'annuaire des destinations actives ne donne pas accès à leur stock. Les coûts sont omis des réponses non OWNER.

SKU normalisé NFKC/majuscules et unique. Variante unique par modèle + couleur normalisée + RAM + stockage, y compris les accessoires avec attributs nuls. RAM et stockage obligatoires pour PHONE. Après mouvement, l'identité est verrouillée ; le journal garde des instantanés. Prix/seuils/état utilisent une version optimiste. Un modèle archivé rend ses variantes inactives ; le modèle doit être réactivé pour réactiver son catalogue.

Une absence de comptage reste non initialisée. Un zéro compté est une ouverture valide. Ouverture seulement avant tout mouvement sur le couple site/variante. Corrections par nouveaux mouvements motivés. À l'expédition : SELLABLE source -q, TRANSIT source +q. À la réception : TRANSIT source -q, SELLABLE destination +q. Aucune disponibilité destination avant réception. Les lignes suivent expédié/reçu/perdu/restant. Réceptions partielles et pertes OWNER sont sérialisées sur le transfert. L'archivage ne bloque pas la réception du transit existant.

Transaction PostgreSQL locale : soldes, journal, transfert, réponse idempotente et outbox. Verrous de variantes et soldes ordonnés. Soldes non négatifs. Triggers interdisant UPDATE/DELETE des journaux et audits. Clé idempotente par utilisateur + opération ; même payload renvoie la réponse d'origine, payload différent = 409. Angular conserve seulement empreinte et clé dans sessionStorage après un résultat inconnu ; aucun jeton ni payload métier.

Pagination des soldes par variante : chaque variante donne une ligne par site autorisé ; total compte les variantes.
Prix BigDecimal à 2 décimales, coût d'ouverture facultatif à 4 décimales. Aucun CUMP ni valorisation calculée. Publication outbox et inbox reportées. Achats, ventes, caisse et crédit hors périmètre.
