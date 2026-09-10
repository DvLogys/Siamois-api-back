package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.history.RevisionSummary;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.EntityDTORegistry;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.openapi.v1.resource.history.EntityVersionResource;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VersionApiServiceTest {

    private static final long IN_SCOPE_ORG = 10L;
    private static final long OUT_OF_SCOPE_ORG = 99L;
    private static final long ENTITY_ID = 42L;

    @Mock
    private HistoryAuditService historyAuditService;
    @Mock
    private EntityDTORegistry entityDTORegistry;
    @Mock
    private EntityManager entityManager;

    private VersionApiService versionApiService;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        versionApiService = new VersionApiService(historyAuditService, entityDTORegistry);
        ReflectionTestUtils.setField(versionApiService, "entityManager", entityManager);
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(IN_SCOPE_ORG), List.of());
        when(entityDTORegistry.getEntityClass(SpatialUnitDTO.class)).thenAnswer(invocation -> SpatialUnit.class);
    }

    private SpatialUnit spatialUnitOwnedBy(Long institutionId) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        SpatialUnit unit = new SpatialUnit();
        unit.setCreatedByInstitution(institution);
        return unit;
    }

    @Test
    void listVersions_returnsRevisionsWhenEntityIsInCallerScope() {
        when(entityManager.find(any(), eq(ENTITY_ID))).thenReturn(spatialUnitOwnedBy(IN_SCOPE_ORG));
        Person author = new Person();
        author.setId(7L);
        when(historyAuditService.findRevisionSummariesForEntity(SpatialUnitDTO.class, ENTITY_ID))
                .thenReturn(List.of(new RevisionSummary(3L, OffsetDateTime.now(), RevisionType.MOD, 7L, "Ada Lovelace")));

        List<EntityVersionResource> versions = versionApiService.listVersions(caller, "spatial-unit", ENTITY_ID);

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).revisionId()).isEqualTo(3L);
        assertThat(versions.get(0).revisionType()).isEqualTo("MOD");
        assertThat(versions.get(0).authorName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void listVersions_refusesEntityOwnedByAnotherOrganization() {
        when(entityManager.find(any(), eq(ENTITY_ID))).thenReturn(spatialUnitOwnedBy(OUT_OF_SCOPE_ORG));

        assertThatThrownBy(() -> versionApiService.listVersions(caller, "spatial-unit", ENTITY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        // Le contrôle de périmètre doit précéder toute lecture des tables _AUD.
        verify(historyAuditService, never()).findRevisionSummariesForEntity(any(), any());
    }

    @Test
    void listVersions_returnsNotFoundWhenEntityDoesNotExist() {
        when(entityManager.find(any(), eq(ENTITY_ID))).thenReturn(null);

        assertThatThrownBy(() -> versionApiService.listVersions(caller, "spatial-unit", ENTITY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(historyAuditService, never()).findRevisionSummariesForEntity(any(), any());
    }

    @Test
    void listVersions_returnsNotFoundForUnknownPanelType() {
        assertThatThrownBy(() -> versionApiService.listVersions(caller, "document", ENTITY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(entityManager, never()).find(any(), any());
    }
}
