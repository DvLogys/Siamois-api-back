package fr.siamois.ui.api.openapi.v1.resource.history;

import java.time.OffsetDateTime;

public record EntityVersionResource(
        long revisionId,
        OffsetDateTime date,
        String revisionType,
        Long authorId,
        String authorName
) {}
