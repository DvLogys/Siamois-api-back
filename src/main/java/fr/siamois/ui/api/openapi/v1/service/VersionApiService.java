package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.history.RevisionSummary;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.EntityDTORegistry;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.ui.api.openapi.v1.resource.history.EntityVersionResource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VersionApiService {

    private final HistoryAuditService historyAuditService;
    private final EntityDTORegistry entityDTORegistry;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<EntityVersionResource> listVersions(ProjectApiCaller caller, String type, Long entityId) {
        PanelResourceTypes panelType = PanelResourceTypes.fromSlug(type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type inconnu : " + type));

        assertEntityInCallerScope(panelType, entityId, caller.accessibleInstitutionIds());

        return historyAuditService
                .findRevisionSummariesForEntity(panelType.dtoClass(), entityId)
                .stream()
                .map(this::toResource)
                .toList();
    }

    // L'historique d'une entité est aussi sensible que l'entité : on vérifie que celle-ci appartient
    // bien à une institution du périmètre de l'appelant avant de lire quoi que ce soit dans les tables _AUD.
    private void assertEntityInCallerScope(PanelResourceTypes panelType, Long entityId,
                                           Set<Long> accessibleInstitutionIds) {
        Class<? extends AbstractEntityDTO> dtoClass = panelType.dtoClass();
        Class<? extends TraceableEntity> entityClass = entityDTORegistry.getEntityClass(dtoClass);
        if (entityClass == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Type inconnu : " + panelType.slug());
        }

        TraceableEntity entity = entityManager.find(entityClass, entityId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource introuvable");
        }

        Institution institution = entity.getCreatedByInstitution();
        if (institution == null || institution.getId() == null
                || !accessibleInstitutionIds.contains(institution.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hors périmètre");
        }
    }

    private EntityVersionResource toResource(RevisionSummary summary) {
        return new EntityVersionResource(
                summary.revisionId(),
                summary.date(),
                summary.revisionType() == null ? null : summary.revisionType().name(),
                summary.authorId(),
                summary.authorName());
    }
}
