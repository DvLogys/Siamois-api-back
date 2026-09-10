package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;

import java.util.Arrays;
import java.util.Optional;

// Types de panneau exposés par l'API, tels qu'attendus par le SPA (EntityPanelType) et tels
// qu'écrits dans les URI de ressource des favoris (/action-unit/58).
public enum PanelResourceTypes {

    SPATIAL_UNIT("spatial-unit", SpatialUnitDTO.class),
    ACTION_UNIT("action-unit", ActionUnitDTO.class),
    RECORDING_UNIT("recording-unit", RecordingUnitDTO.class),
    SPECIMEN("specimen", SpecimenDTO.class),
    CONTAINER("container", ContainerDTO.class),
    PHASE("phase", PhaseDTO.class);

    private final String slug;
    private final Class<? extends AbstractEntityDTO> dtoClass;

    PanelResourceTypes(String slug, Class<? extends AbstractEntityDTO> dtoClass) {
        this.slug = slug;
        this.dtoClass = dtoClass;
    }

    public String slug() {
        return slug;
    }

    public Class<? extends AbstractEntityDTO> dtoClass() {
        return dtoClass;
    }

    public String resourceUri(Object id) {
        return "/" + slug + "/" + id;
    }

    public static Optional<PanelResourceTypes> fromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String candidate = slug.trim();
        return Arrays.stream(values())
                .filter(type -> type.slug.equals(candidate))
                .findFirst();
    }

    public static Optional<PanelResourceTypes> fromResourceUri(String resourceUri) {
        if (resourceUri == null || resourceUri.isBlank()) {
            return Optional.empty();
        }
        String path = resourceUri.startsWith("/") ? resourceUri.substring(1) : resourceUri;
        int separator = path.indexOf('/');
        return fromSlug(separator < 0 ? path : path.substring(0, separator));
    }
}
