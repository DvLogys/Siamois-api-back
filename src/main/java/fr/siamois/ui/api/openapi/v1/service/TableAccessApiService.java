package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.resource.table.TableCreatePolicyResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ce que l'appelant a le droit de faire sur un tableau : créer, et modifier quelles lignes.
 *
 * Chaque panneau du JSF pose son propre {@code ToolbarCreateConfig} sur son modèle de tableau : la
 * liste générale des lieux autorise la création si l'appelant gère les lieux de l'organisation,
 * celle des UE ne l'autorise jamais (il n'y aurait pas de projet où les ranger) et affiche à la
 * place un message et un lien, et l'onglet UE d'un projet l'autorise pour qui a le droit d'écrire
 * dans ce projet. Les règles reprises ici sont celles de ces panneaux, une par une.
 */
@Service
@RequiredArgsConstructor
public class TableAccessApiService {

    /** D'où le tableau est regardé : la liste générale, ou l'onglet d'une fiche. */
    public enum Scope {
        ORGANIZATION, ACTION_UNIT, SPATIAL_UNIT, RECORDING_UNIT;

        static Scope of(String value) {
            if (value == null || value.isBlank()) {
                return ORGANIZATION;
            }
            return PanelResourceTypes.fromSlug(value)
                    .map(type -> switch (type) {
                        case ACTION_UNIT -> ACTION_UNIT;
                        case SPATIAL_UNIT -> SPATIAL_UNIT;
                        case RECORDING_UNIT -> RECORDING_UNIT;
                        default -> throw unknownScope(value);
                    })
                    .orElseThrow(() -> unknownScope(value));
        }

        private static ResponseStatusException unknownScope(String value) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contexte de création inconnu : " + value);
        }
    }

    private final ProfilePermissionService profilePermissionService;
    private final LangService langService;

    /**
     * Parmi les lignes d'une page, celles que l'appelant peut modifier.
     *
     * Le droit se décide par projet, et il est résolu pour toute la page d'un coup plutôt qu'une
     * requête par ligne — c'est ce que fait {@code EntityTableViewModel#canEditByActionUnit}.
     *
     * Une différence assumée avec le JSF : les codes vérifiés sont ceux que l'enregistrement
     * applique réellement ({@code ProfilePermissionService#hasRecordingUnitWritePermission} et ses
     * pendants), c'est-à-dire la cascade INSTANCE → ORGANISATION → PROJET. Le {@code canUserEditRow}
     * du JSF ne passe que le code projet, aux deux niveaux : un gestionnaire d'organisation, qui
     * détient {@code ORGANIZATION_EDIT_*} et non {@code PROJECT_EDIT_*}, y voit donc ses cellules en
     * lecture alors que le serveur accepterait sa modification. Annoncer un droit plus étroit que
     * celui réellement appliqué rendrait le tableau inutilisable pour ces comptes.
     *
     * Le mode écriture du bandeau, que le JSF combine à ce droit, reste au client : c'est un état
     * d'affichage, pas une permission.
     *
     * @param type           le type d'entité listé
     * @param projectIdByRow le projet de chaque ligne, indexé par identifiant de ligne
     * @param userInfo       l'appelant, dans son institution
     * @return les identifiants des lignes modifiables
     */
    public Set<Long> editableRowIds(PanelResourceTypes type, Map<Long, Long> projectIdByRow, UserInfo userInfo) {
        if (projectIdByRow.isEmpty()) {
            return Set.of();
        }
        RowEditPermission permission = rowEditPermissionOf(type);

        // Un droit détenu au niveau instance ou organisation vaut pour toutes les lignes : inutile
        // d'interroger les projets un par un.
        if (profilePermissionService.hasInstancePermission(userInfo.getUser(), permission.instanceCode())
                || profilePermissionService.hasOrganizationPermission(userInfo, permission.organizationCode())) {
            return Set.copyOf(projectIdByRow.keySet());
        }
        if (permission.projectCode() == null) {
            return Set.of();
        }

        Set<Long> projectIds = projectIdByRow.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> editableProjects = profilePermissionService.actionUnitIdsWithPermission(
                userInfo, projectIds, permission.projectCode());

        return projectIdByRow.entrySet().stream()
                .filter(row -> row.getValue() != null && editableProjects.contains(row.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * La cascade de codes qui autorise la modification d'une ligne, telle que l'enregistrement
     * l'applique. Un code projet nul signale un droit qui ne se délègue pas par projet : un lieu
     * appartient à l'organisation, pas à un projet.
     */
    private record RowEditPermission(String instanceCode, String organizationCode, String projectCode) {}

    private static RowEditPermission rowEditPermissionOf(PanelResourceTypes type) {
        return switch (type) {
            case RECORDING_UNIT -> new RowEditPermission(PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
                    PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS,
                    PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);
            case SPECIMEN -> new RowEditPermission(PermissionConstants.INSTANCE_EDIT_FINDS,
                    PermissionConstants.ORGANIZATION_EDIT_FINDS, PermissionConstants.PROJECT_EDIT_FINDS);
            case PHASE -> new RowEditPermission(PermissionConstants.INSTANCE_EDIT_PHASES,
                    PermissionConstants.ORGANIZATION_EDIT_PHASES, PermissionConstants.PROJECT_EDIT_PHASES);
            case CONTAINER -> new RowEditPermission(PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                    PermissionConstants.ORGANIZATION_EDIT_CONTAINERS, PermissionConstants.PROJECT_EDIT_CONTAINERS);
            case ACTION_UNIT -> new RowEditPermission(PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS,
                    PermissionConstants.ORGANIZATION_MANAGE_ACTIONS, PermissionConstants.PROJECT_MANAGE_SETTINGS);
            case SPATIAL_UNIT -> new RowEditPermission(PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_PLACES,
                    PermissionConstants.ORGANIZATION_MANAGE_PLACES, null);
        };
    }

    public TableCreatePolicyResource getPolicy(PanelResourceTypes type, ProjectApiCaller caller,
                                               InstitutionDTO institution, String scopeType, Long scopeId,
                                               String lang) {
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        Scope scope = Scope.of(scopeType);
        Locale locale = langService.localeForApiLang(lang);

        return switch (scope) {
            case ORGANIZATION -> fromOrganizationList(type, userInfo, locale);
            case ACTION_UNIT -> fromProject(type, userInfo, requireScopeId(scopeId), locale);
            case SPATIAL_UNIT -> fromPlace(type, userInfo, locale);
            case RECORDING_UNIT -> fromRecordingUnit(type, userInfo, requireScopeId(scopeId), locale);
        };
    }

    /** Les listes générales : {@code *ListPanel}. */
    private TableCreatePolicyResource fromOrganizationList(PanelResourceTypes type, UserInfo userInfo, Locale locale) {
        return switch (type) {
            case SPATIAL_UNIT -> allowed(type, profilePermissionService.hasOrganizationPermission(
                    userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES));
            case ACTION_UNIT -> allowed(type, profilePermissionService.hasActionUnitCreatePermission(userInfo));
            // Ces entités n'existent que rattachées : la liste générale n'a pas de quoi les créer.
            case RECORDING_UNIT -> unavailable(type, "recordingunit.toolbar.createRequiresActionUnit",
                    "common.action.selectProject", locale);
            case SPECIMEN -> unavailable(type, "specimen.toolbar.createRequiresRecordingUnit",
                    "common.action.selectRecordingUnit", locale);
            case PHASE -> unavailable(type, "phase.toolbar.createRequiresActionUnit",
                    "common.action.selectProject", locale);
            case CONTAINER -> unavailable(type, "container.toolbar.createRequiresActionUnit",
                    "common.action.selectProject", locale);
        };
    }

    /** Les onglets d'un projet : {@code ActionUnitPanel}. */
    private TableCreatePolicyResource fromProject(PanelResourceTypes type, UserInfo userInfo, Long projectId,
                                                  Locale locale) {
        return switch (type) {
            case RECORDING_UNIT -> allowed(type, hasProjectPermission(userInfo, projectId,
                    PermissionConstants.PROJECT_EDIT_RECORDING_UNITS));
            case CONTAINER -> allowed(type, hasProjectPermission(userInfo, projectId,
                    PermissionConstants.PROJECT_EDIT_CONTAINERS));
            case PHASE -> allowed(type, hasProjectPermission(userInfo, projectId,
                    PermissionConstants.PROJECT_EDIT_PHASES));
            case SPECIMEN -> unavailable(type, "specimen.toolbar.createRequiresRecordingUnit",
                    "common.action.selectRecordingUnit", locale);
            default -> notCreatableHere(type);
        };
    }

    /** Les onglets d'un lieu : {@code SpatialUnitPanel}. */
    private TableCreatePolicyResource fromPlace(PanelResourceTypes type, UserInfo userInfo, Locale locale) {
        return switch (type) {
            case ACTION_UNIT -> allowed(type, profilePermissionService.hasActionUnitCreatePermission(userInfo));
            case SPATIAL_UNIT -> allowed(type, profilePermissionService.hasOrganizationPermission(
                    userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES));
            case RECORDING_UNIT -> unavailable(type, "recordingunit.toolbar.createRequiresActionUnit",
                    "common.action.selectProject", locale);
            default -> notCreatableHere(type);
        };
    }

    /** Les onglets d'une UE : {@code RecordingUnitPanel}. Le projet est celui de l'UE. */
    private TableCreatePolicyResource fromRecordingUnit(PanelResourceTypes type, UserInfo userInfo, Long projectId,
                                                        Locale locale) {
        return switch (type) {
            case RECORDING_UNIT -> allowed(type, hasProjectPermission(userInfo, projectId,
                    PermissionConstants.PROJECT_EDIT_RECORDING_UNITS));
            case SPECIMEN -> allowed(type, hasProjectPermission(userInfo, projectId,
                    PermissionConstants.PROJECT_EDIT_FINDS));
            default -> notCreatableHere(type);
        };
    }

    private boolean hasProjectPermission(UserInfo userInfo, Long projectId, String permissionCode) {
        return projectId != null && profilePermissionService.hasProjectPermission(userInfo, projectId, permissionCode);
    }

    private static Long requireScopeId(Long scopeId) {
        return Optional.ofNullable(scopeId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "scopeId est requis pour ce contexte de création"));
    }

    private static TableCreatePolicyResource allowed(PanelResourceTypes type, boolean allowed) {
        return new TableCreatePolicyResource(type.slug(), allowed, null, null);
    }

    private TableCreatePolicyResource unavailable(PanelResourceTypes type, String messageKey, String linkLabelKey,
                                                  Locale locale) {
        return new TableCreatePolicyResource(type.slug(), false,
                langService.resolveMessage(messageKey, locale),
                langService.resolveMessage(linkLabelKey, locale));
    }

    /** Le JSF ne pose aucune barre de création pour cette entité sur cet écran. */
    private static TableCreatePolicyResource notCreatableHere(PanelResourceTypes type) {
        return new TableCreatePolicyResource(type.slug(), false, null, null);
    }
}
