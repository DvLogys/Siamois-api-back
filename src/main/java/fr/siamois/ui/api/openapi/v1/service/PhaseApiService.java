package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.request.phase.PhaseWriteRequest;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class PhaseApiService {

    private final ProjectApiService projectApiService;
    private final PhaseService phaseService;
    private final PhaseRepository phaseRepository;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final LabelService labelService;
    private final ProfilePermissionService profilePermissionService;

    @Transactional(readOnly = true)
    public PhaseResource getPhase(ProjectApiCaller caller, Long phaseId, String lang) {
        PhaseDTO phase = requireAccessiblePhase(caller, phaseId);
        return toResource(phase, lang);
    }

    @Transactional
    public PhaseResource createPhase(ProjectApiCaller caller, String projectIdOrKey,
                                     PhaseWriteRequest request, String lang) {
        AccessibleProjectForApi accessible = projectApiService.requireAccessibleProject(caller, projectIdOrKey);
        ActionUnitDTO project = accessible.actionUnit();

        PhaseDTO phase = new PhaseDTO();
        phase.setActionUnit(toSummary(project));
        // `PhaseService.save` ne renseigne pas la traçabilité : dans le JSF c'est l'appelant qui la
        // pose (cf. GenericNewUnitDialogBean, EntityFormContext). Sans ça, `createdBy` viole son
        // @NotNull au moment du persist.
        phase.setCreatedBy(caller.person());
        phase.setCreatedByInstitution(project.getCreatedByInstitution());
        phase.setValidated(ValidationStatus.INCOMPLETE);
        applyRequest(phase, request, true);

        return toResource(savePhase(caller, project, phase), lang);
    }

    @Transactional
    public PhaseResource patchPhase(ProjectApiCaller caller, Long phaseId, PhaseWriteRequest request, String lang) {
        PhaseDTO phase = requireAccessiblePhase(caller, phaseId);
        applyRequest(phase, request, false);

        ActionUnitDTO project = projectOf(caller, phase);
        return toResource(savePhase(caller, project, phase), lang);
    }

    @Transactional
    public void deletePhase(ProjectApiCaller caller, Long phaseId) {
        PhaseDTO phase = requireAccessiblePhase(caller, phaseId);
        ActionUnitDTO project = projectOf(caller, phase);

        UserInfo userInfo = new UserInfo(project.getCreatedByInstitution(), caller.person(), null);
        if (!profilePermissionService.hasPhaseWritePermission(userInfo, phase)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification de la phase non autorisée");
        }
        phaseRepository.deleteById(phaseId);
    }

    /**
     * {@code PhaseService.save} lit lui-même l'utilisateur courant dans {@code ExecutionContextHolder}
     * pour vérifier {@code PROJECT_EDIT_PHASES} : hors JSF ce contexte n'est posé par aucun filtre,
     * il faut donc l'ouvrir explicitement autour de l'appel.
     */
    private PhaseDTO savePhase(ProjectApiCaller caller, ActionUnitDTO project, PhaseDTO phase) {
        UserInfo userInfo = new UserInfo(project.getCreatedByInstitution(), caller.person(), null);
        try {
            return OpenApiExecutionContext.callWithUserInfo(userInfo, () -> phaseService.save(phase));
        } catch (ForbiddenOperationException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification de la phase non autorisée");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private PhaseDTO requireAccessiblePhase(ProjectApiCaller caller, Long phaseId) {
        PhaseDTO phase = phaseService.findById(phaseId);
        if (phase == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase introuvable");
        }
        // Passe par le contrôle de projet : une phase n'est lisible que si son projet l'est.
        projectOf(caller, phase);
        return phase;
    }

    private ActionUnitDTO projectOf(ProjectApiCaller caller, PhaseDTO phase) {
        if (phase.getActionUnit() == null || phase.getActionUnit().getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase sans projet");
        }
        return projectApiService
                .requireAccessibleProject(caller, String.valueOf(phase.getActionUnit().getId()))
                .actionUnit();
    }

    // PATCH : seuls les champs présents sont appliqués. À la création tout est pris tel quel.
    private void applyRequest(PhaseDTO phase, PhaseWriteRequest request, boolean isCreation) {
        if (request == null) {
            return;
        }
        applyIfPresent(request.identifier(), phase::setIdentifier, isCreation);
        applyIfPresent(request.title(), phase::setTitle, isCreation);
        applyIfPresent(request.description(), phase::setDescription, isCreation);
        applyIfPresent(request.orderNumber(), phase::setOrderNumber, isCreation);
        applyIfPresent(request.lowerBound(), phase::setLowerBound, isCreation);
        applyIfPresent(request.upperBound(), phase::setUpperBound, isCreation);

        if (request.typeConceptId() != null) {
            phase.setType(requireConcept(request.typeConceptId()));
        }
        if (request.periodConceptIds() != null) {
            phase.setPeriods(requireConcepts(request.periodConceptIds()));
        }
        if (request.keywordConceptIds() != null) {
            phase.setKeywords(requireConcepts(request.keywordConceptIds()));
        }
    }

    private <T> void applyIfPresent(T value, Consumer<T> setter, boolean isCreation) {
        if (value != null || isCreation) {
            setter.accept(value);
        }
    }

    private ConceptDTO requireConcept(Long conceptId) {
        Concept concept = conceptService.findById(conceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Concept introuvable : " + conceptId));
        return conceptMapper.convert(concept);
    }

    private Set<ConceptDTO> requireConcepts(Collection<Long> conceptIds) {
        Set<ConceptDTO> concepts = new LinkedHashSet<>();
        for (Long conceptId : conceptIds) {
            concepts.add(requireConcept(conceptId));
        }
        return concepts;
    }

    // Le générateur d'identifiant lit `fullIdentifier` de l'unité d'action (jeton ID_UA) :
    // un résumé réduit à l'identifiant technique ne suffit pas.
    private ActionUnitSummaryDTO toSummary(ActionUnitDTO project) {
        ActionUnitSummaryDTO summary = new ActionUnitSummaryDTO();
        summary.setId(project.getId());
        summary.setIdentifier(project.getIdentifier());
        summary.setFullIdentifier(project.getFullIdentifier());
        summary.setCreatedByInstitution(project.getCreatedByInstitution());
        return summary;
    }

    private PhaseResource toResource(PhaseDTO phase, String lang) {
        PhaseResource resource = new PhaseResource();
        resource.setId(phase.getId() == null ? null : String.valueOf(phase.getId()));
        resource.setIdentifier(phase.getIdentifier());
        resource.setTitle(phase.getTitle());
        resource.setLabel(phase.getTitle() != null && !phase.getTitle().isBlank()
                ? phase.getTitle()
                : phase.getIdentifier());
        resource.setDescription(phase.getDescription());
        resource.setOrderNumber(phase.getOrderNumber());
        resource.setLowerBound(phase.getLowerBound());
        resource.setUpperBound(phase.getUpperBound());
        resource.setProjectId(phase.getActionUnit() == null ? null : phase.getActionUnit().getId());
        resource.setType(toConceptResource(phase.getType(), lang));
        resource.setPeriods(toConceptResources(phase.getPeriods(), lang));
        resource.setKeywords(toConceptResources(phase.getKeywords(), lang));
        return resource;
    }

    private List<ResolvedConceptResource> toConceptResources(Collection<ConceptDTO> concepts, String lang) {
        if (concepts == null) {
            return List.of();
        }
        return concepts.stream().map(concept -> toConceptResource(concept, lang)).toList();
    }

    private ResolvedConceptResource toConceptResource(ConceptDTO concept, String lang) {
        if (concept == null) {
            return null;
        }
        ResolvedConceptResource resource = new ResolvedConceptResource();
        resource.setResourceType("concepts");
        resource.setId(String.valueOf(concept.getId()));
        resource.setExternalUrl(concept.getExternalId());
        ConceptLabelDTO label = labelService.findLabelOf(concept, lang);
        resource.setResolvedLabel(label == null ? null : label.getLabel());
        return resource;
    }
}
