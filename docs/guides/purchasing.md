# Achats, fournisseurs et contrôle des réceptions
29 octobre 2026.

Ouvrir http://localhost:4300 puis Achats & réceptions. Le module est accessible au propriétaire et aux responsables stock affectés ; le caissier ne le voit pas.

## Fournisseurs et commande (propriétaire)
Créer un fournisseur avec nom et téléphone (facultatif), sans inventer ses informations légales. Modifier ou archiver depuis Fournisseurs ; une ancienne commande garde le nom enregistré lors de sa création.
Nouvelle commande : choisir fournisseur actif, site de réception, référence interne unique et motif. Ajouter les variantes, quantités entières et coûts unitaires convenus (jusqu'à quatre décimales). Recherche et chargement des pages sont indépendants pour fournisseurs et variantes. Confirmer commande ne crée aucun stock et ne comptabilise pas de dette.

## Réception physique (propriétaire ou responsable du site)
Ouvrir la commande, cliquer Réception physique. Saisir référence du bon fournisseur, motif et quantités réellement comptées. Zéro permet d'omettre une ligne non livrée, mais au moins une quantité positive est nécessaire.
Le disponible n'augmente pas. Les unités reçues entrent en QUARANTAINE et attendent le contrôle du propriétaire. Une réception ne peut dépasser le reste commandé. Le même bon fournisseur ne doit pas être saisi deux fois sur la même commande.

## Contrôle (propriétaire)
Ouvrir Réceptions et contrôles puis Contrôler sur une réception. Indiquer quantité acceptée et quantité refusée après vérification physique, avec motif. Le total ne peut dépasser ce qui reste à contrôler. Un contrôle partiel est possible.
L'accepté devient disponible. Le refusé reste isolé dans Refusé ; il ne peut pas être vendu ni transféré depuis le disponible. Le retour/remplacement et l'avoir fournisseur seront traités dans un prochain module.

## Clôture et suivi
Clôturer le reliquat annule seulement le non reçu. Les réceptions existantes, le stock et les contrôles en attente restent conservés. La clôture ne supprime ni document ni dette (aucune dette n'est calculée dans ce jalon).
Stock par établissement montre disponible, transit, attendu, quarantaine et refusé. Le journal garde réception fournisseur, contrôle accepté et contrôle refusé avec auteur et date Casablanca.
Après une erreur de résultat incertain, réessayer sans changer les champs. Si la commande était enregistrée, sa réponse est retrouvée sans duplication, y compris pendant une panne temporaire de Finance. Une erreur définitive nécessite de vérifier les données et les accès.
