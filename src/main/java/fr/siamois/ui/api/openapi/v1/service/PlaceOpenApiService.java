package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.PlaceOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.place.PlacePatchRequest;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse;
import fr.siamois.domain.models.form.customform.CustomFormComposer;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import java.util.Locale;
import java.util.Map;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PlaceOpenApiService {

    private final ProjectApiService projectApiService;
    private final InstitutionService institutionService;
    private final SpatialUnitService spatialUnitService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final ProfilePermissionService profilePermissionService;
    private final PlaceOpenApiMapper placeOpenApiMapper;
    private final FormAnswerApiService formAnswerApiService;
    private final FormLayoutApiMapper formLayoutApiMapper;
    private final LangService langService;
    private final FormAnswerWriteApiService formAnswerWriteApiService;
    private final FormService formService;

    @Transactional(readOnly = true)
    public PlaceListResponse listByOrganization(ProjectApiCaller caller,
                                              long organizationId,
                                              int offset,
                                              int limit,
                                              String sortParam,
                                              String lang) {
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());

        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }

        Sort sort = ProjectApiService.parsePlaceSort(sortParam);
        Page<SpatialUnitDTO> page = spatialUnitService.findByInstitutionId(organizationId, limit, offset, sort);

        var resources = page.getContent().stream()
                .map(dto -> placeOpenApiMapper.toResource(dto, lang))
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return new PlaceListResponse(resources, meta);
    }

    @Transactional
    public PlaceCreatedResponse.PlaceCreatedItem createPlace(ProjectApiCaller caller,
                                                             PlaceCreateRequest request,
                                                             String lang) {
        if (request == null || request.getOrganizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        projectApiService.assertOrganizationInCallerScope(
                request.getOrganizationId(), caller.accessibleInstitutionIds());

        InstitutionDTO institution = institutionService.findById(request.getOrganizationId());
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }

        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de lieu non autorisée");
        }

        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name est obligatoire");
        }
        if (request.getTypeConceptId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "typeConceptId est obligatoire");
        }

        Concept typeConcept = conceptService.findById(request.getTypeConceptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de lieu introuvable"));
        ConceptDTO category = conceptMapper.convert(typeConcept);

        SpatialUnitDTO toSave = new SpatialUnitDTO();
        toSave.setName(name);
        toSave.setCategory(category);
        toSave.setPlaceNumber(request.getPlaceNumber());
        if (request.getAddress() != null) {
            toSave.setAddress(request.getAddress());
        }

        try {
            SpatialUnitDTO saved = spatialUnitService.save(userInfo, toSave);
            return new PlaceCreatedResponse.PlaceCreatedItem(
                    saved.getId(), saved.getName(), saved.getCode(), saved.getPlaceNumber());
        } catch (SpatialUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    @Transactional
    public PlaceCreatedResponse.PlaceCreatedItem updatePlace(ProjectApiCaller caller,
                                                             long placeId,
                                                             PlacePatchRequest patch,
                                                             String lang) {
        if (patch == null) {
            patch = new PlacePatchRequest();
        }
        SpatialUnitDTO dto = requireAccessiblePlace(caller, placeId);
        requirePlaceWritePermission(caller, dto, lang, "Modification de lieu non autorisée");

        InstitutionDTO institution = dto.getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);

        ConceptDTO category = null;
        if (patch.getTypeConceptId() != null) {
            Concept typeConcept = conceptService.findById(patch.getTypeConceptId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de lieu introuvable"));
            category = conceptMapper.convert(typeConcept);
        }

        if (patch.getName() != null && patch.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name ne peut pas être vide");
        }

        try {
            SpatialUnitDTO saved = patch.isPlaceNumberPresent()
                    ? spatialUnitService.updatePlace(userInfo, placeId, patch.getName(), category, patch.getAddress(),
                            patch.getPlaceNumber(), true)
                    : spatialUnitService.updatePlace(userInfo, placeId, patch.getName(), category, patch.getAddress());
            return new PlaceCreatedResponse.PlaceCreatedItem(
                    saved.getId(), saved.getName(), saved.getCode(), saved.getPlaceNumber());
        } catch (SpatialUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    @Transactional
    public void deletePlace(ProjectApiCaller caller, long placeId, String lang) {
        SpatialUnitDTO dto = requireAccessiblePlace(caller, placeId);
        requirePlaceWritePermission(caller, dto, lang, "Suppression de lieu non autorisée");
        try {
            spatialUnitService.deleteIfUnused(placeId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (SpatialUnitNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieu introuvable");
        }
    }

    /**
     * La fiche d'un lieu, formulaire compris.
     *
     * Un lieu se décrit par le même formulaire dynamique qu'une UE — {@code SpatialUnit.DETAILS_FORM},
     * que {@code SpatialUnitPanel} rend côté JSF. La fiche porte donc ses réponses et la disposition
     * de ses sections, sans quoi le client ne pourrait qu'empiler des valeurs en lecture.
     *
     * @param caller  l'appelant
     * @param placeId le lieu demandé
     * @param lang    la langue des libellés
     * @return la fiche du lieu
     */
    @Transactional(readOnly = true)
    public PlaceResource getPlace(ProjectApiCaller caller, long placeId, String lang) {
        SpatialUnitDTO dto = requireAccessiblePlace(caller, placeId);
        PlaceResource resource = placeOpenApiMapper.toResource(dto, lang);

        Locale locale = langService.localeForApiLang(lang);
        UserInfo userInfo = new UserInfo(dto.getCreatedByInstitution(), caller.person(), lang);
        // Le formulaire est résolu dans le contexte de l'appelant : les libellés et les valeurs de
        // vocabulaire en dépendent.
        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            FormUiDto form = CustomFormComposer.deepCopy(SpatialUnit.DETAILS_FORM);
            resource.setAnswers(formAnswerApiService.buildAnswers(dto, new PanelFieldSource(form), locale));
            resource.setLayout(formLayoutApiMapper.toLayout(form, locale));
        });
        return resource;
    }

    /**
     * Enregistre les réponses de formulaire d'un lieu.
     *
     * Même chemin que pour une UE : les valeurs reçues sont converties selon le type de leur champ,
     * posées sur les réponses de la fiche, puis répercutées sur l'entité avant sauvegarde.
     *
     * @param caller  l'appelant
     * @param placeId le lieu modifié
     * @param answers les valeurs reçues, indexées par identifiant de champ
     * @param lang    la langue des libellés
     * @return la fiche relue
     */
    @Transactional
    public PlaceResource patchPlaceAnswers(ProjectApiCaller caller, long placeId,
                                           Map<String, Object> answers, String lang) {
        SpatialUnitDTO dto = requireAccessiblePlace(caller, placeId);
        requirePlaceWritePermission(caller, dto, lang, "Modification de lieu non autorisée");
        if (answers == null || answers.isEmpty()) {
            return getPlace(caller, placeId, lang);
        }

        UserInfo userInfo = new UserInfo(dto.getCreatedByInstitution(), caller.person(), lang);
        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            FormUiDto form = CustomFormComposer.deepCopy(SpatialUnit.DETAILS_FORM);
            FieldSource fieldSource = new PanelFieldSource(form);
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            formAnswerWriteApiService.applyAnswers(response, fieldSource, answers);
            formService.updateJpaEntityFromResponse(response, dto);
            spatialUnitService.save(dto);
        });
        return getPlace(caller, placeId, lang);
    }

    private SpatialUnitDTO requireAccessiblePlace(ProjectApiCaller caller, long placeId) {
        SpatialUnitDTO dto;
        try {
            dto = spatialUnitService.findById(placeId);
        } catch (SpatialUnitNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieu introuvable");
        }
        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lieu sans organisation de rattachement");
        }
        projectApiService.assertOrganizationInCallerScope(institution.getId(), caller.accessibleInstitutionIds());
        return dto;
    }

    private void requirePlaceWritePermission(ProjectApiCaller caller,
                                             SpatialUnitDTO dto,
                                             String lang,
                                             String forbiddenMessage) {
        InstitutionDTO institution = dto.getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
    }
}
