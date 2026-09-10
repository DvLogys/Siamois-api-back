package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gardes d'accès aux écrans de paramétrage, exposées une seule fois pour que membres, profils et
 * thésaurus appliquent exactement la même règle que les pages JSF correspondantes.
 */
@Service
@RequiredArgsConstructor
public class SettingsAccessApiService {

    private final ProjectApiService projectApiService;
    private final ProfilePermissionService profilePermissionService;

    // Reprend canManageInstitution() d'InstitutionDetailsBean. La cascade de ProfilePermissionService
    // ne vaut que pour un code identique : un profil INSTANCE ne détient donc pas
    // ORGANIZATION_MANAGE_SETTINGS, d'où le contrôle explicite des deux codes.
    public InstitutionDTO requireManageableOrganization(ProjectApiCaller caller, Long organizationId) {
        InstitutionDTO organization = projectApiService.requireOrganization(organizationId, caller);
        PersonDTO person = caller.person();
        boolean allowed = profilePermissionService.hasInstancePermission(
                        person, PermissionConstants.INSTANCE_MANAGE_SETTINGS)
                || profilePermissionService.hasOrganizationPermission(
                        person, organization, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Paramétrage de l'organisation non autorisé");
        }
        return organization;
    }

    // Reprend checkProjectOrRedirect() de ProjectDetailsBean : un seul code, dont la cascade
    // remonte vers l'organisation puis l'instance.
    public ActionUnitDTO requireManageableProject(ProjectApiCaller caller, String projectId) {
        AccessibleProjectForApi accessible = projectApiService.requireAccessibleProject(caller, projectId);
        ActionUnitDTO project = accessible.actionUnit();
        UserInfo userInfo = new UserInfo(project.getCreatedByInstitution(), caller.person(), null);
        if (!profilePermissionService.hasProjectPermission(userInfo, project.getId(),
                PermissionConstants.PROJECT_MANAGE_SETTINGS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Paramétrage du projet non autorisé");
        }
        return project;
    }
}
