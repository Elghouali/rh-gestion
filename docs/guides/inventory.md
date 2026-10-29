# Utiliser le catalogue et le stock RH
29 octobre 2026.

## Accès
Lancer ./tools/start-local.ps1 et ouvrir http://localhost:4300. Utiliser le compte local et son mot de passe dans .env. Cliquer Produits & stock. Les noms et affectations de démonstration doivent être confirmés avant usage réel.

## Catalogue (propriétaire)
Créer un modèle avec nom, marque et catégorie. Créer ensuite une variante : SKU unique, couleur, RAM et stockage pour un téléphone, prix gros/détail et seuil minimum. Un téléphone noir 8/128 et bleu 8/256 correspond à deux variantes. Les prix acceptent la virgule ou le point.
Rechercher par SKU, couleur, marque ou modèle. Les formulaires ont une recherche indépendante avec chargement des pages. Modifier le modèle depuis la liste des modèles ; modifier ou archiver la variante dans le tableau. L'historique reste visible. Pour réactiver un modèle archivé, réactiver le modèle puis ses variantes nécessaires.
L'identité est verrouillée après mouvement : créer une autre variante si l'identité physique change.

## Premier inventaire
Le propriétaire compte physiquement les variantes du dépôt et des quatre magasins. Stock initial demande site, variante, quantité et motif. Zéro est valide si le comptage confirme le vide ; coût vide signifie inconnu. Non initialisé ne signifie pas zéro confirmé.
Après le premier mouvement, saisir un Ajustement signé et motivé, par exemple -2 pour deux unités manquantes. Le disponible ne peut pas devenir négatif. Les créations locales [DEMO E2E] sont des données de test.

## Transfert
Un gestionnaire choisit une source affectée, une destination active et les lignes à expédier. Les unités passent en transit : indisponibles à la source et pas encore disponibles à destination.
Le propriétaire ou un responsable affecté à la destination réceptionne les quantités réellement reçues. Plusieurs réceptions partielles sont possibles ; le reste demeure en transit. Seul le propriétaire constate une perte en transit avec motif. Le caissier ne réceptionne pas dans ce jalon.
Un transfert déjà réceptionné se corrige par un transfert inverse autorisé, sans modifier le journal. Le traitement d'un transit restant dépend de la situation physique et d'une décision du propriétaire.

## Contrôle et réessai
Stock par établissement affiche disponible, transit sortant et attendu entrant. Le seuil signale les variantes actives comptées sous le minimum. Le journal montre auteur, date Casablanca, motif, écart et solde après mouvement. Les caissiers voient leurs sites et pas les coûts. Français, arabe RTL et anglais sont disponibles.
Après une coupure ou une erreur serveur, conserver les mêmes champs et réessayer dans le même onglet : la clé idempotente est conservée. Vérifier le journal après rétablissement. Ne pas changer la quantité pour forcer un résultat. Corriger une erreur de version ou de stock avant une nouvelle demande.
