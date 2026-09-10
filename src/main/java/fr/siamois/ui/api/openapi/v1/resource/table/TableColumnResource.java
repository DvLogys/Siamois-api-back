package fr.siamois.ui.api.openapi.v1.resource.table;

import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import org.springframework.lang.Nullable;

/**
 * Une colonne de tableau telle que le JSF la définit ({@code fr.siamois.ui.table.column.TableColumn}
 * et ses sous-classes), traduite pour un client qui ne rend pas de composant PrimeFaces.
 *
 * Ce qui est repris : l'ordre (celui de la liste), la visibilité par défaut, la possibilité de
 * masquer la colonne, le tri et le filtre. Ce qui ne l'est pas : les expressions ajax
 * ({@code process}, {@code update}, {@code onstart}) — elles n'ont de sens que dans le cycle de vie
 * Faces.
 */
public record TableColumnResource(

        /* Identifiant technique de la colonne, clé de la visibilité enregistrée dans une vue. */
        String id,

        /* FORM_FIELD | COMMAND_LINK | RELATION | ACTIONS */
        String type,

        /* En-tête déjà résolu selon Accept-Language : le JSF stocke une clé de message. */
        String header,

        /* Propriété de la ligne portant la valeur affichée (ou le compteur, pour une relation). */
        @Nullable String valueKey,

        boolean visible,
        boolean toggleable,

        boolean sortable,
        /* Valeur à passer au paramètre `sort`. Null quand la colonne n'est pas triable. */
        @Nullable String sortField,

        boolean filterable,
        /* Nature du filtre, qui détermine le composant de saisie côté client. */
        @Nullable String filterKind,
        /* Clé à passer au paramètre `filter`. Null quand la colonne n'est pas filtrable. */
        @Nullable String filterField,

        /* Le champ de formulaire derrière la colonne : présent pour les seules colonnes FORM_FIELD. */
        @Nullable FieldResource field,

        boolean required,
        boolean readOnly,

        @Nullable String icon,
        @Nullable String color,
        @Nullable String width,
        @Nullable String styleClass
) {
}
