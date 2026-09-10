package fr.siamois.ui.api.openapi.v1.resource.table;

import org.springframework.lang.Nullable;

/**
 * Ce que la barre d'outils d'un tableau propose de créer, dans le contexte où il est affiché.
 *
 * Transposition de {@code ToolbarCreateConfig}, que chaque panneau JSF pose sur son modèle de
 * tableau : la même entité ne se crée pas partout. Une UE ne se crée pas depuis la liste générale —
 * il n'y aurait pas de projet à la rattacher — mais depuis l'onglet UE d'un projet, et seulement
 * pour qui a le droit d'y écrire. Le JSF affiche alors le message d'indisponibilité et son lien à
 * la place du bouton, plutôt que de laisser la barre vide.
 */
public record TableCreatePolicyResource(

        /* Type d'entité créée par le bouton. */
        String resourceType,

        /* Vrai si l'appelant peut créer ici et maintenant. */
        boolean allowed,

        /* Explication affichée à la place du bouton quand la création n'est pas possible ici. */
        @Nullable String unavailableMessage,

        /* Libellé du lien accompagnant cette explication. */
        @Nullable String unavailableLinkLabel
) {
}
