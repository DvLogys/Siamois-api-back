package fr.siamois.domain.models.history;

import org.hibernate.envers.RevisionType;

import java.time.OffsetDateTime;

/**
 * Métadonnées d'une révision Envers, sans l'instantané de l'entité : de quoi alimenter un onglet
 * « Versions » sans payer la conversion en DTO de chaque révision.
 */
public record RevisionSummary(
        long revisionId,
        OffsetDateTime date,
        RevisionType revisionType,
        Long authorId,
        String authorName
) {}
