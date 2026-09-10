package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanelResourceTypesTest {

    @Test
    void fromSlug_resolvesEveryPanelTypeExposedToTheSpa() {
        assertThat(PanelResourceTypes.fromSlug("action-unit")).contains(PanelResourceTypes.ACTION_UNIT);
        assertThat(PanelResourceTypes.fromSlug("recording-unit")).contains(PanelResourceTypes.RECORDING_UNIT);
        assertThat(PanelResourceTypes.fromSlug("spatial-unit")).contains(PanelResourceTypes.SPATIAL_UNIT);
        assertThat(PanelResourceTypes.fromSlug("specimen")).contains(PanelResourceTypes.SPECIMEN);
        assertThat(PanelResourceTypes.fromSlug("container")).contains(PanelResourceTypes.CONTAINER);
        assertThat(PanelResourceTypes.fromSlug("phase")).contains(PanelResourceTypes.PHASE);
    }

    @Test
    void fromSlug_rejectsUnknownOrEmptyValues() {
        assertThat(PanelResourceTypes.fromSlug("document")).isEmpty();
        assertThat(PanelResourceTypes.fromSlug("")).isEmpty();
        assertThat(PanelResourceTypes.fromSlug(null)).isEmpty();
    }

    @Test
    void fromResourceUri_readsTypeFromBookmarkUri() {
        assertThat(PanelResourceTypes.fromResourceUri("/action-unit/58")).contains(PanelResourceTypes.ACTION_UNIT);
        assertThat(PanelResourceTypes.fromResourceUri("/spatial-unit")).contains(PanelResourceTypes.SPATIAL_UNIT);
        assertThat(PanelResourceTypes.fromResourceUri("recording-unit/12")).contains(PanelResourceTypes.RECORDING_UNIT);
    }

    @Test
    void fromResourceUri_returnsEmptyForNonEntityUri() {
        assertThat(PanelResourceTypes.fromResourceUri("/welcome")).isEmpty();
        assertThat(PanelResourceTypes.fromResourceUri("")).isEmpty();
        assertThat(PanelResourceTypes.fromResourceUri(null)).isEmpty();
    }

    @Test
    void resourceUri_matchesTheUriFormatUsedByBookmarks() {
        assertThat(PanelResourceTypes.ACTION_UNIT.resourceUri(58L)).isEqualTo("/action-unit/58");
    }

    @Test
    void dtoClass_mapsToTheDtoExpectedByTheAuditRegistry() {
        assertThat(PanelResourceTypes.ACTION_UNIT.dtoClass()).isEqualTo(ActionUnitDTO.class);
        assertThat(PanelResourceTypes.RECORDING_UNIT.dtoClass()).isEqualTo(RecordingUnitDTO.class);
    }
}
