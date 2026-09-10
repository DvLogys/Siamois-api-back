package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsAccessApiServiceTest {

    private static final long ORG_ID = 10L;
    private static final long PROJECT_ID = 77L;

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private ProfilePermissionService profilePermissionService;

    private SettingsAccessApiService settingsAccessApiService;
    private ProjectApiCaller caller;
    private InstitutionDTO organization;

    @BeforeEach
    void setUp() {
        settingsAccessApiService = new SettingsAccessApiService(projectApiService, profilePermissionService);
        organization = new InstitutionDTO();
        organization.setId(ORG_ID);
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(ORG_ID), List.of(organization));
        when(projectApiService.requireOrganization(eq(ORG_ID), any())).thenReturn(organization);
    }

    /**
     * Le point délicat documenté dans docs/permissions.md : la cascade ne vaut que pour un code
     * identique. Un super-administrateur ne détient pas ORGANIZATION_MANAGE_SETTINGS, il doit donc
     * passer par le contrôle explicite du code INSTANCE.
     */
    @Test
    void requireManageableOrganization_allowsInstanceManagerWithoutOrganizationPermission() {
        when(profilePermissionService.hasInstancePermission(any(), eq(PermissionConstants.INSTANCE_MANAGE_SETTINGS)))
                .thenReturn(true);
        when(profilePermissionService.hasOrganizationPermission(any(), any(), any())).thenReturn(false);

        assertThat(settingsAccessApiService.requireManageableOrganization(caller, ORG_ID)).isSameAs(organization);
    }

    @Test
    void requireManageableOrganization_allowsOrganizationManager() {
        when(profilePermissionService.hasInstancePermission(any(), any())).thenReturn(false);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(organization),
                eq(PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))).thenReturn(true);

        assertThat(settingsAccessApiService.requireManageableOrganization(caller, ORG_ID)).isSameAs(organization);
    }

    @Test
    void requireManageableOrganization_refusesPlainMember() {
        when(profilePermissionService.hasInstancePermission(any(), any())).thenReturn(false);
        when(profilePermissionService.hasOrganizationPermission(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> settingsAccessApiService.requireManageableOrganization(caller, ORG_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void requireManageableProject_refusesWithoutProjectManageSettings() {
        when(projectApiService.requireAccessibleProject(any(), any())).thenReturn(accessibleProject());
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> settingsAccessApiService.requireManageableProject(caller, "77"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void requireManageableProject_allowsProjectManager() {
        when(projectApiService.requireAccessibleProject(any(), any())).thenReturn(accessibleProject());
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(PROJECT_ID),
                eq(PermissionConstants.PROJECT_MANAGE_SETTINGS))).thenReturn(true);

        assertThat(settingsAccessApiService.requireManageableProject(caller, "77").getId()).isEqualTo(PROJECT_ID);
    }

    private AccessibleProjectForApi accessibleProject() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(organization);
        return new AccessibleProjectForApi(project, 0L, 0L);
    }
}
