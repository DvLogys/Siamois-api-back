package fr.siamois.ui.api.openapi.v1.request.phase;

import java.util.List;

public record PhaseWriteRequest(
        String identifier,
        String title,
        String description,
        Integer orderNumber,
        Integer lowerBound,
        Integer upperBound,
        Long typeConceptId,
        List<Long> periodConceptIds,
        List<Long> keywordConceptIds
) {}
