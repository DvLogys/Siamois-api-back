package fr.siamois.ui.api.openapi.v1.resource.table;

import java.util.List;

/**
 * Les colonnes d'un type d'entité, dans l'ordre où le tableau les pose.
 */
public record TableDefinitionResource(
        String resourceType,
        List<TableColumnResource> columns
) {
}
