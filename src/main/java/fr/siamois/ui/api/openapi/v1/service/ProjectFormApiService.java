package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customform.CustomFormComposer;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

/**
 * Le formulaire de la fiche d'un projet.
 *
 * Un projet se décrit par le même formulaire dynamique qu'une UE — {@code ActionUnit.DETAILS_FORM},
 * que {@code ActionUnitPanel} rend côté JSF. Ce service pose ses réponses sur la fiche et les
 * enregistre, en s'appuyant sur les deux services partagés à toutes les fiches.
 */
@Service
@RequiredArgsConstructor
public class ProjectFormApiService {

    private final ActionUnitService actionUnitService;
    private final ProfilePermissionService profilePermissionService;
    private final FormService formService;
    private final FormAnswerApiService formAnswerApiService;
    private final FormAnswerWriteApiService formAnswerWriteApiService;
    private final FormLayoutApiMapper formLayoutApiMapper;
    private final LangService langService;

    /** Complète une fiche de projet de ses réponses de formulaire et de leur disposition. */
    @Transactional(readOnly = true)
    public void attachForm(ProjectResource resource, ActionUnitDTO dto, ProjectApiCaller caller, String lang) {
        Locale locale = langService.localeForApiLang(lang);
        UserInfo userInfo = new UserInfo(dto.getCreatedByInstitution(), caller.person(), lang);
        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            FormUiDto form = CustomFormComposer.deepCopy(ActionUnit.DETAILS_FORM);
            resource.setAnswers(formAnswerApiService.buildAnswers(dto, new PanelFieldSource(form), locale));
            resource.setLayout(formLayoutApiMapper.toLayout(form, locale));
        });
    }

    /**
     * Enregistre les réponses du formulaire d'un projet.
     *
     * @param caller    l'appelant
     * @param dto       le projet modifié
     * @param answers   les valeurs reçues, indexées par identifiant de champ
     * @param lang      la langue des libellés
     */
    @Transactional
    public void saveAnswers(ProjectApiCaller caller, ActionUnitDTO dto, Map<String, Object> answers, String lang) {
        UserInfo userInfo = new UserInfo(dto.getCreatedByInstitution(), caller.person(), lang);
        if (!profilePermissionService.hasActionUnitWritePermission(userInfo, dto)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification de projet non autorisée");
        }
        if (answers == null || answers.isEmpty()) {
            return;
        }
        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            FormUiDto form = CustomFormComposer.deepCopy(ActionUnit.DETAILS_FORM);
            FieldSource fieldSource = new PanelFieldSource(form);
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            formAnswerWriteApiService.applyAnswers(response, fieldSource, answers);
            formService.updateJpaEntityFromResponse(response, dto);
            actionUnitService.save(dto);
        });
    }
}
